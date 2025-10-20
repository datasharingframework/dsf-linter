package dev.dsf.linter.util.resource;

import dev.dsf.linter.util.cache.ConcurrentCache;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

import static dev.dsf.linter.classloading.ProjectClassLoaderFactory.getOrCreateProjectClassLoader;

/**
 * Utility class for resolving resource references in various formats to concrete files, URLs, or streams.
 *
 * <p>The resolver supports lookups in the following order:</p>
 * <ol>
 *   <li>Resource root directory (determined by {@link ResourceRootResolver})</li>
 *   <li>Additional search paths within resource root (bpmn, fhir subdirectories)</li>
 *   <li>Absolute file paths</li>
 *   <li>Classpath resources (from dependencies and plugin JARs)</li>
 * </ol>
 *
 * @since 1.0
 */
public final class ResourceResolver {

    /**
     * Cache key format: "canonicalProjectRoot::classpathPath"
     */
    private static final ConcurrentCache<String, File> MATERIALIZED_CACHE = new ConcurrentCache<>(file -> {
        try {
            if (file.exists()) {
                Files.deleteIfExists(file.toPath());

                Path parent = file.toPath().getParent();
                if (parent != null && Files.exists(parent)) {
                    try {
                        Files.deleteIfExists(parent);
                    } catch (Exception ignored) {
                        // Parent might not be empty
                    }
                }
            }
        } catch (Exception ignored) {
            // Best effort cleanup
        }
    });

    /**
     * Cache for resolved resource roots.
     * Key: canonical project root path
     * Value: resolved resource root directory
     */
    private static final ConcurrentCache<String, File> RESOURCE_ROOT_CACHE = new ConcurrentCache<>();

    private ResourceResolver() {}

    /**
     * Normalizes a resource reference coming from plugin definitions.
     * Accepts variants like:
     *  - "src/main/resources/fhir/CodeSystem/x.xml"
     *  - "fhir/CodeSystem/x.xml"
     *  - "/fhir/CodeSystem/x.xml"
     *  - "classpath:fhir/CodeSystem/x.xml"
     *  - "fhir\\CodeSystem\\x.xml" (Windows backslashes)
     * Returns a classpath-friendly path such as "fhir/CodeSystem/x.xml".
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

        // Convert Windows backslashes to forward slashes for classpath compatibility
        // ClassLoader.getResource() expects forward slashes regardless of OS
        r = r.replace('\\', '/');

        return r;
    }

    /**
     * Resolve a resource reference to a concrete File with default search paths.
     *
     * @param ref the resource reference to resolve
     * @param projectRoot the project root directory
     * @return Optional containing the resolved File, or empty if not found
     */
    public static Optional<File> resolveToFile(String ref, File projectRoot) {
        return resolveToFile(ref, projectRoot, "bpmn", "fhir");
    }

    /**
     * Resolve a resource reference to a concrete File:
     * 1) Check disk using centralized resource root resolution
     * 2) Fallback: resolve via project URLClassLoader and materialize to a temp file
     *
     * @param ref the resource reference to resolve
     * @param projectRoot the project root directory
     * @param additionalSearchPaths additional subdirectories to search in resource root
     * @return Optional containing the resolved File, or empty if not found
     */
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

    /**
     * Alternative method that returns a URL instead of materializing to a file.
     * More efficient when the consumer can work with URLs/InputStreams directly.
     *
     * @param ref the resource reference to resolve
     * @param projectRoot the project root directory
     * @return Optional containing the resolved URL, or empty if not found
     */
    public static Optional<URL> resolveToURL(String ref, File projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        String cpPath = normalizeRef(ref);
        if (cpPath.isEmpty()) return Optional.empty();

        // 1) Check disk first using centralized resource root
        Optional<File> diskResult = searchOnDisk(cpPath, projectRoot);
        if (diskResult.isPresent()) {
            try {
                return Optional.of(diskResult.get().toURI().toURL());
            } catch (Exception e) {
                // fall through to classpath
            }
        }

        // 2) Classpath lookup
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            URL url = cl.getResource(cpPath);
            return Optional.ofNullable(url);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Alternative method that returns an InputStream directly.
     * Most efficient when the consumer only needs to read the resource once.
     *
     * @param ref the resource reference to resolve
     * @param projectRoot the project root directory
     * @return Optional containing the InputStream, or empty if not found
     */
    public static Optional<InputStream> resolveToStream(String ref, File projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        String cpPath = normalizeRef(ref);
        if (cpPath.isEmpty()) return Optional.empty();

        // 1) Check disk first using centralized resource root
        Optional<File> diskResult = searchOnDisk(cpPath, projectRoot);
        if (diskResult.isPresent()) {
            try {
                return Optional.of(new FileInputStream(diskResult.get()));
            } catch (Exception e) {
                // fall through to classpath
            }
        }

        // 2) Classpath lookup
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            InputStream stream = cl.getResourceAsStream(cpPath);
            return Optional.ofNullable(stream);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Clears all caches (materialized resources and resolved resource roots).
     * Invokes cleanup callbacks to delete temporary files.
     */
    public static void clearCache() {
        MATERIALIZED_CACHE.clear();
        RESOURCE_ROOT_CACHE.clear();
    }

    /**
     * Returns cache statistics for monitoring and debugging.
     *
     * @return a map containing cache names and their sizes
     */
    public static Map<String, Integer> getCacheStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("materialized", MATERIALIZED_CACHE.size());
        stats.put("resourceRoot", RESOURCE_ROOT_CACHE.size());
        return stats;
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