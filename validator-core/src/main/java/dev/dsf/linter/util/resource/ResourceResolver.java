package dev.dsf.linter.util.resource;

import dev.dsf.linter.util.cache.ConcurrentCache;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

import static dev.dsf.linter.classloading.ProjectClassLoaderFactory.getOrCreateProjectClassLoader;

/**
 * Utility class for resolving resource references in various formats to concrete files, URLs, or streams.
 * Enhanced with strict resource root validation and dependency JAR scanning.
 *
 * @since 1.0
 */
public final class ResourceResolver {

    /**
     * Result of resource resolution with location tracking.
     */
    public enum ResolutionSource {
        DISK_IN_ROOT,           // Found on disk within expected resource root
        DISK_OUTSIDE_ROOT,      // Found on disk but outside expected resource root
        CLASSPATH_MATERIALIZED, // Found via classpath and materialized (legacy)
        CLASSPATH_DEPENDENCY,   // Found in dependency JAR (target/dependency/*.jar)
        NOT_FOUND               // Not found anywhere
    }

    /**
     * Enhanced resolution result with metadata about where resource was found.
     */
    public record ResolutionResult(
            Optional<File> file,
            ResolutionSource source,
            String expectedRoot,
            String actualLocation
    ) {
        public static ResolutionResult notFound(String expectedRoot) {
            return new ResolutionResult(
                    Optional.empty(),
                    ResolutionSource.NOT_FOUND,
                    expectedRoot,
                    null
            );
        }

        public static ResolutionResult inRoot(File file, File expectedRoot) {
            return new ResolutionResult(
                    Optional.of(file),
                    ResolutionSource.DISK_IN_ROOT,
                    expectedRoot.getAbsolutePath(),
                    file.getAbsolutePath()
            );
        }

        public static ResolutionResult outsideRoot(File file, File expectedRoot) {
            return new ResolutionResult(
                    Optional.of(file),
                    ResolutionSource.DISK_OUTSIDE_ROOT,
                    expectedRoot.getAbsolutePath(),
                    file.getAbsolutePath()
            );
        }

        public static ResolutionResult fromDependency(File file, String dependencyJar, File expectedRoot) {
            return new ResolutionResult(
                    Optional.of(file),
                    ResolutionSource.CLASSPATH_DEPENDENCY,
                    expectedRoot.getAbsolutePath(),
                    "dependency:" + dependencyJar
            );
        }

        public boolean isValid() {
            return source == ResolutionSource.DISK_IN_ROOT ||
                    source == ResolutionSource.CLASSPATH_DEPENDENCY;
        }
    }

    private static final ConcurrentCache<String, File> MATERIALIZED_CACHE = new ConcurrentCache<>(file -> {
        try {
            if (file.exists()) {
                Files.deleteIfExists(file.toPath());

                Path parent = file.toPath().getParent();
                if (parent != null && Files.exists(parent)) {
                    try {
                        Files.deleteIfExists(parent);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    });

    private static final ConcurrentCache<String, File> RESOURCE_ROOT_CACHE = new ConcurrentCache<>();

    private ResourceResolver() {}

    /**
     * Normalizes a resource reference coming from plugin definitions.
     */
    public static String normalizeRef(String ref) {
        if (ref == null) return "";
        String r = ref.trim();

        // strip "classpath:" prefix
        if (r.startsWith("classpath:")) {
            r = r.substring("classpath:".length());
        }

        // strip leading src/main/resources/
        if (r.startsWith("src/main/resources/")) {
            r = r.substring("src/main/resources/".length());
        }

        // normalize leading slash/backslash
        while (r.startsWith("/") || r.startsWith("\\")) {
            r = r.substring(1);
        }

        r = r.replace('\\', '/');

        return r;
    }

    /**
     * Resolves resource with strict validation against expected resource root.
     * Enhanced to search in dependency JARs if not found on disk.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Search on disk in expectedResourceRoot</li>
     *   <li>If found and inside root → DISK_IN_ROOT</li>
     *   <li>If found and outside root → DISK_OUTSIDE_ROOT</li>
     *   <li>If not found on disk → search in dependency JARs</li>
     *   <li>If found in dependencies → CLASSPATH_DEPENDENCY</li>
     *   <li>Otherwise → NOT_FOUND</li>
     * </ol>
     * </p>
     *
     * @param ref the resource reference from plugin definition
     * @param expectedResourceRoot the resource root directory for this specific plugin
     * @param projectRoot the project root directory for classpath search
     * @return resolution result with metadata about where the file was found
     */
    public static ResolutionResult resolveToFileStrict(String ref, File expectedResourceRoot, File projectRoot) {
        Objects.requireNonNull(expectedResourceRoot, "expectedResourceRoot");
        String cpPath = normalizeRef(ref);
        if (cpPath.isEmpty()) {
            return ResolutionResult.notFound(expectedResourceRoot.getAbsolutePath());
        }

        // 1. Disk search
        Optional<File> diskResult = searchOnDisk(cpPath, expectedResourceRoot);

        if (diskResult.isPresent()) {
            File resolved = diskResult.get();

            // 2. Validate: File must be under expected resource root
            if (isUnderDirectory(resolved, expectedResourceRoot)) {
                return ResolutionResult.inRoot(resolved, expectedResourceRoot);
            } else {
                return ResolutionResult.outsideRoot(resolved, expectedResourceRoot);
            }
        }

        // 3. Not found on disk → search in dependency JARs
        if (projectRoot != null) {
            Optional<DependencyResolutionResult> dependencyResult = searchInDependencyJars(cpPath, projectRoot);
            if (dependencyResult.isPresent()) {
                return ResolutionResult.fromDependency(
                        dependencyResult.get().materializedFile(),
                        dependencyResult.get().sourceJar(),
                        expectedResourceRoot
                );
            }
        }

        // 4. Not found anywhere
        return ResolutionResult.notFound(expectedResourceRoot.getAbsolutePath());
    }

    /**
     * Result of dependency JAR search.
     */
    private record DependencyResolutionResult(File materializedFile, String sourceJar) {}

    /**
     * Searches for a resource in dependency JARs (target/dependency/*.jar).
     * If found, materializes the resource to a temporary file.
     *
     * @param cpPath the normalized classpath path
     * @param projectRoot the project root directory
     * @return optional containing materialized file and source JAR name
     */
    private static Optional<DependencyResolutionResult> searchInDependencyJars(String cpPath, File projectRoot) {
        try {
            String canonicalRoot = projectRoot.getCanonicalPath();
            String cacheKey = canonicalRoot + "::dep::" + cpPath;

            File cachedFile = MATERIALIZED_CACHE.getOrCreate(cacheKey, k -> {
                try {
                    ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
                    URL url = cl.getResource(cpPath);
                    if (url == null) return null;

                    // Check if URL is from a JAR
                    String urlString = url.toString();
                    if (!urlString.startsWith("jar:file:")) {
                        return null; // Not from JAR, skip
                    }

                    // Extract JAR name from URL
                    // Format: jar:file:/path/to/project/target/dependency/some-lib.jar!/path/to/resource
                    String jarPath = urlString.substring("jar:file:".length(), urlString.indexOf("!"));
                    File jarFile = new File(jarPath);

                    // Verify it's from target/dependency
                    if (!jarFile.getAbsolutePath().contains("target" + File.separator + "dependency")) {
                        return null; // Not from dependency folder
                    }

                    // Materialize resource
                    Path tempRoot = Files.createTempDirectory("dsf-validator-dependency-");
                    return getFile(cpPath, url, tempRoot);
                } catch (Exception e) {
                    return null;
                }
            });

            if (cachedFile != null) {
                // Extract JAR name for logging
                String jarName = extractJarNameFromClasspath(cpPath, projectRoot);
                return Optional.of(new DependencyResolutionResult(cachedFile, jarName));
            }

            return Optional.empty();

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Extracts the dependency JAR name from classpath for a given resource.
     */
    private static String extractJarNameFromClasspath(String cpPath, File projectRoot) {
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            URL url = cl.getResource(cpPath);
            if (url != null && url.toString().startsWith("jar:file:")) {
                String jarPath = url.toString().substring("jar:file:".length(), url.toString().indexOf("!"));
                return new File(jarPath).getName();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "unknown-dependency.jar";
    }

    /**
     * Checks if file is located under the specified directory.
     *
     * @param file the file to check
     * @param directory the directory that should contain the file
     * @return true if file is under directory, false otherwise
     */
    public static boolean isUnderDirectory(File file, File directory) {
        try {
            Path filePath = file.getCanonicalFile().toPath();
            Path dirPath = directory.getCanonicalFile().toPath();
            return filePath.startsWith(dirPath);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Legacy method - resolves to file with default search paths.
     * Kept for backward compatibility.
     *
     * @deprecated Use resolveToFileStrict() for validation scenarios
     */
    @Deprecated
    public static Optional<File> resolveToFile(String ref, File projectRoot) {
        return resolveToFile(ref, projectRoot, "bpmn", "fhir");
    }

    /**
     * Legacy method - resolves to file with classpath fallback.
     * Kept for backward compatibility.
     *
     * @deprecated Use resolveToFileStrict() for validation scenarios
     */
    @Deprecated
    public static Optional<File> resolveToFile(String ref, File projectRoot, String... additionalSearchPaths) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        String cpPath = normalizeRef(ref);
        if (cpPath.isEmpty()) return Optional.empty();

        // 1) Disk lookups using centralized resource root
        Optional<File> diskResult = searchOnDisk(cpPath, projectRoot, additionalSearchPaths);
        if (diskResult.isPresent()) {
            return diskResult;
        }

        // Also accept an absolute path the plugin might have recorded
        File abs = new File(ref);
        if (abs.isAbsolute() && abs.isFile()) {
            return Optional.of(abs);
        }

        // 2) Classpath lookup (dependencies and plugin JAR) with caching
        return materializeFromClasspath(cpPath, projectRoot);
    }

    private static Optional<File> searchOnDisk(String cpPath, File projectRoot, String... additionalSearchPaths) {
        try {
            // Get or compute cached resource root
            String cacheKey = projectRoot.getCanonicalPath();
            File resourceRoot = RESOURCE_ROOT_CACHE.getOrCreate(cacheKey, k -> {
                ResourceRootResolver.ResolutionResult result =
                        ResourceRootResolver.resolveResourceRoot(projectRoot);
                return result.resourceRoot();
            });

            // 1) Direct lookup in resource root
            File direct = new File(resourceRoot, cpPath);
            if (direct.isFile()) {
                return Optional.of(direct);
            }

            // 2) Check additional search paths within resource root
            for (String additionalPath : additionalSearchPaths) {
                File additional = new File(resourceRoot, additionalPath + "/" + cpPath);
                if (additional.isFile()) {
                    return Optional.of(additional);
                }
            }

            return Optional.empty();

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<File> materializeFromClasspath(String cpPath, File projectRoot) {
        try {
            String canonicalRoot = projectRoot.getCanonicalPath();
            String cacheKey = canonicalRoot + "::" + cpPath;

            return Optional.ofNullable(MATERIALIZED_CACHE.getOrCreate(cacheKey, k -> {
                try {
                    ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
                    URL url = cl.getResource(cpPath);
                    if (url == null) return null;

                    Path tempRoot = Files.createTempDirectory("dsf-validator-resources-");
                    return getFile(cpPath, url, tempRoot);
                } catch (Exception e) {
                    return null;
                }
            }));

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static File getFile(String cpPath, URL url, Path tempRoot) throws IOException {
        tempRoot.toFile().deleteOnExit();

        Path targetPath = tempRoot.resolve(cpPath);
        Files.createDirectories(targetPath.getParent());

        try (InputStream in = url.openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        File result = targetPath.toFile();
        result.deleteOnExit();

        return result;
    }
}