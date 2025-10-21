package dev.dsf.linter.util.resource;

import dev.dsf.linter.util.cache.ConcurrentCache;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

import static dev.dsf.linter.classloading.ProjectClassLoaderFactory.getOrCreateProjectClassLoader;

/**
 * Utility class for resolving resource references in various formats to concrete files, URLs, or streams.
 * Enhanced with strict resource root validation to prevent classpath pollution.
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
        CLASSPATH_MATERIALIZED, // Found via classpath and materialized
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

        public boolean isValid() {
            return source == ResolutionSource.DISK_IN_ROOT;
        }

        public boolean hasIssue() {
            return source == ResolutionSource.DISK_OUTSIDE_ROOT ||
                    source == ResolutionSource.CLASSPATH_MATERIALIZED;
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
     * This is the recommended method for validation scenarios.
     *
     * @param ref the resource reference from plugin definition
     * @param expectedResourceRoot the resource root directory for this specific plugin
     * @return resolution result with metadata about where the file was found
     */
    public static ResolutionResult resolveToFileStrict(String ref, File expectedResourceRoot) {
        Objects.requireNonNull(expectedResourceRoot, "expectedResourceRoot");
        String cpPath = normalizeRef(ref);
        if (cpPath.isEmpty()) {
            return ResolutionResult.notFound(expectedResourceRoot.getAbsolutePath());
        }

        // 1. Disk search
        Optional<File> diskResult = searchOnDisk(cpPath, expectedResourceRoot);

        if (diskResult.isEmpty()) {
            return ResolutionResult.notFound(expectedResourceRoot.getAbsolutePath());
        }

        File resolved = diskResult.get();

        // 2. Validate: File must be under expected resource root
        if (isUnderDirectory(resolved, expectedResourceRoot)) {
            return ResolutionResult.inRoot(resolved, expectedResourceRoot);
        } else {
            return ResolutionResult.outsideRoot(resolved, expectedResourceRoot);
        }
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
                    tempRoot.toFile().deleteOnExit();

                    Path targetPath = tempRoot.resolve(cpPath);
                    Files.createDirectories(targetPath.getParent());

                    try (InputStream in = url.openStream()) {
                        Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    File result = targetPath.toFile();
                    result.deleteOnExit();

                    return result;
                } catch (Exception e) {
                    return null;
                }
            }));

        } catch (Exception e) {
            return Optional.empty();
        }
    }
}