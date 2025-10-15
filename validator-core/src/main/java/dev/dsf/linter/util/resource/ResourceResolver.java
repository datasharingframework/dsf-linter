package dev.dsf.linter.util.resource;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static dev.dsf.linter.classloading.ProjectClassLoaderFactory.getOrCreateProjectClassLoader;

/**
 * Utility class for resolving resource references in various formats to concrete files, URLs, or streams.
 *
 * <p>This class provides functionality to normalize resource paths and resolve them from
 * multiple sources including the file system and classpath. It integrates with {@link ResourceRootResolver}
 * for consistent resource root directory determination.</p>
 *
 * <p>The resolver supports lookups in the following order:</p>
 * <ol>
 *   <li>Resource root directory (determined by {@link ResourceRootResolver})</li>
 *   <li>Additional search paths within resource root (bpmn, fhir subdirectories)</li>
 *   <li>Absolute file paths</li>
 *   <li>Classpath resources (from dependencies and plugin JARs)</li>
 * </ol>
 *
 * <p>For classpath resources that cannot be accessed directly as files (e.g., resources
 * inside JAR files), the resolver provides multiple access methods:</p>
 * <ul>
 *   <li>{@link #resolveToFile(String, File)} - Materializes resources as temporary files</li>
 *   <li>{@link #resolveToURL(String, File)} - Returns URL for direct access (more efficient)</li>
 *   <li>{@link #resolveToStream(String, File)} - Returns InputStream for one-time reading (most efficient)</li>
 * </ul>
 *
 * <p>A cache is maintained for materialized temporary files to avoid repeated I/O operations
 * when using the file-based API. The cache can be cleared using {@link #clearCache()}.</p>
 *
 * @since 1.0
 */
public final class ResourceResolver {

    /**
     * Cache for materialized classpath resources to avoid repeated I/O operations.
     * Key format: "canonicalProjectRoot::classpathPath"
     */
    private static final Map<String, File> MATERIALIZED_CACHE = new ConcurrentHashMap<>();

    /**
     * Cache for resolved resource roots to avoid repeated resolution.
     * Key: canonical project root path
     * Value: resolved resource root directory
     */
    private static final Map<String, File> RESOURCE_ROOT_CACHE = new ConcurrentHashMap<>();

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
     * This keeps the existing File-based validator API without duplicating parsing logic.
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
     * Useful for testing or memory management.
     */
    public static void clearCache() {
        // Clean up temp files before clearing cache
        MATERIALIZED_CACHE.values().forEach(file -> {
            try {
                if (file.exists()) {
                    Files.deleteIfExists(file.toPath());
                    // Also try to delete parent temp directory if empty
                    Path parent = file.toPath().getParent();
                    if (parent != null && Files.exists(parent)) {
                        try {
                            Files.deleteIfExists(parent);
                        } catch (Exception ignored) {
                            // Parent might not be empty, ignore
                        }
                    }
                }
            } catch (Exception ignored) {
                // Best effort cleanup
            }
        });
        MATERIALIZED_CACHE.clear();
        RESOURCE_ROOT_CACHE.clear();
    }

    //  Private helper methods

    /**
     * Searches for the resource on disk using centralized resource root resolution.
     *
     * OPTIMIZATION: This method now uses {@link ResourceRootResolver} to determine
     * the resource root directory, eliminating duplicate logic and ensuring consistency.
     *
     * @param cpPath the normalized classpath-style resource path
     * @param projectRoot the project root directory
     * @param additionalSearchPaths additional subdirectories to search (e.g., "bpmn", "fhir")
     * @return Optional containing the resolved File, or empty if not found
     */
    private static Optional<File> searchOnDisk(String cpPath, File projectRoot, String... additionalSearchPaths) {
        try {
            // Get or compute cached resource root
            String cacheKey = projectRoot.getCanonicalPath();
            File resourceRoot = RESOURCE_ROOT_CACHE.computeIfAbsent(cacheKey, k -> {
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

            // 3) Special case: Check if the path already contains one of the additional paths
            // This handles cases like "bpmn/process.bpmn" when resource is at "resourceRoot/bpmn/process.bpmn"
            for (String additionalPath : additionalSearchPaths) {
                if (cpPath.startsWith(additionalPath + "/")) {
                    // Path already includes the subdirectory, try direct lookup again
                    // This is already covered by step 1, so we can skip
                    break;
                }
            }

            return Optional.empty();

        } catch (Exception e) {
            // If anything fails, return empty
            return Optional.empty();
        }
    }

    /**
     * Materializes a classpath resource to a temporary file with caching and directory structure preservation.
     *
     * @param cpPath the normalized classpath-style resource path
     * @param projectRoot the project root directory
     * @return Optional containing the materialized File, or empty if not found
     */
    private static Optional<File> materializeFromClasspath(String cpPath, File projectRoot) {
        try {
            String canonicalRoot = projectRoot.getCanonicalPath();
            String cacheKey = canonicalRoot + "::" + cpPath;

            // Check cache first
            File cached = MATERIALIZED_CACHE.get(cacheKey);
            if (cached != null && cached.exists()) {
                return Optional.of(cached);
            }

            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            URL url = cl.getResource(cpPath);
            if (url == null) return Optional.empty();

            // Create temp directory structure that preserves the original path structure
            // This avoids filename collisions when different directories contain files with same names
            Path tempRoot = Files.createTempDirectory("dsf-validator-resources-");
            tempRoot.toFile().deleteOnExit();

            Path targetPath = tempRoot.resolve(cpPath);

            // Ensure parent directories exist
            Files.createDirectories(targetPath.getParent());

            // Copy with REPLACE_EXISTING in case cache creates the file again
            try (InputStream in = url.openStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            File result = targetPath.toFile();
            result.deleteOnExit();

            // Cache the result
            MATERIALIZED_CACHE.put(cacheKey, result);

            return Optional.of(result);

        } catch (Exception e) {
            return Optional.empty();
        }
    }
}