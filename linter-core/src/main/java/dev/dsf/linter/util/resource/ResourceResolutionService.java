package dev.dsf.linter.util.resource;

import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

import dev.dsf.linter.util.cache.ConcurrentCache;
import static dev.dsf.linter.classloading.ProjectClassLoaderFactory.getOrCreateProjectClassLoader;

/**
 * Service for resolving and validating resource references with strict linting.
 * <p>
 * This service performs resource resolution with validation against expected resource roots.
 * It searches for resources in the following order:
 * </p>
 * <ol>
 *   <li>On disk within the expected resource root directory</li>
 *   <li>On disk outside the expected resource root (flagged as warning)</li>
 *   <li>In dependency JARs via classpath lookup</li>
 * </ol>
 * <p>
 * The service maintains caches for materialized resources and resource root lookups
 * to improve performance during repeated resolution operations.
 * </p>
 * <p>
 * This service is thread-safe and can be used concurrently by multiple threads.
 * </p>
 */
public class ResourceResolutionService {

    private final ConcurrentCache<String, File> materializedCache;
    private final ConcurrentCache<String, File> resourceRootCache;

    /**
     * Creates a new resolution service with unified caching.
     */
    public ResourceResolutionService() {
        this.materializedCache = new ConcurrentCache<>(file -> {
            try {
                if (file != null && file.exists()) {
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

        this.resourceRootCache = new ConcurrentCache<>();
    }

    /**
     * Resolves resource with strict linting against expected resource root.
     * <p>
     * Resolution order:
     * </p>
     * <ol>
     *   <li>Search on disk in expectedResourceRoot</li>
     *   <li>If found inside root → DISK_IN_ROOT</li>
     *   <li>If found outside root → DISK_OUTSIDE_ROOT</li>
     *   <li>If not found on disk → search in dependency JARs</li>
     *   <li>If found in dependencies → CLASSPATH_DEPENDENCY</li>
     *   <li>Otherwise → NOT_FOUND</li>
     * </ol>
     *
     * @param ref the resource reference from plugin definition
     * @param expectedResourceRoot the resource root directory for this plugin
     * @param projectRoot the project root directory for dependency search
     * @return resolution result with metadata
     */
    public ResourceResolutionResult resolveStrict(String ref, File expectedResourceRoot, File projectRoot) {
        Objects.requireNonNull(expectedResourceRoot, "expectedResourceRoot cannot be null");

        String normalizedPath = ResourcePathNormalizer.normalize(ref);
        if (normalizedPath.isEmpty()) {
            return ResourceResolutionResult.notFound(expectedResourceRoot.getAbsolutePath());
        }

        // Step 1: Disk search
        Optional<File> diskResult = searchOnDisk(normalizedPath, expectedResourceRoot);

        if (diskResult.isPresent()) {
            File resolved = diskResult.get();

            // Step 2: Validate location
            if (isUnderDirectory(resolved, expectedResourceRoot)) {
                return ResourceResolutionResult.inRoot(resolved, expectedResourceRoot);
            } else {
                return ResourceResolutionResult.outsideRoot(resolved, expectedResourceRoot);
            }
        }

        // Step 3: Search in dependency JARs
        if (projectRoot != null) {
            Optional<DependencyResolution> dependencyResult = searchInDependencies(normalizedPath, projectRoot);
            if (dependencyResult.isPresent()) {
                return ResourceResolutionResult.fromDependency(
                        dependencyResult.get().file(),
                        dependencyResult.get().jarName(),
                        expectedResourceRoot
                );
            }
        }

        // Step 4: Not found
        return ResourceResolutionResult.notFound(expectedResourceRoot.getAbsolutePath());
    }

    /**
     * Collects all BPMN paths referenced by the plugin.
     *
     * @param adapter the plugin adapter
     * @return set of normalized BPMN paths
     */
    public Set<String> collectBpmnPaths(PluginAdapter adapter) {
        return adapter.getProcessModels().stream()
                .map(ResourcePathNormalizer::normalize)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    /**
     * Collects all FHIR paths referenced by the plugin.
     *
     * @param adapter the plugin adapter
     * @return set of normalized FHIR paths
     */
    public Set<String> collectFhirPaths(PluginAdapter adapter) {
        return adapter.getFhirResourcesByProcessId().values().stream()
                .flatMap(Collection::stream)
                .map(ResourcePathNormalizer::normalize)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    /**
     * Resolves multiple resource references with strict linting.
     *
     * @param paths the paths to resolve
     * @param expectedRoot the expected resource root
     * @param projectRoot the project root
     * @return categorized resolution results
     */
    public ResolvedResources resolveMultiple(Set<String> paths, File expectedRoot, File projectRoot) {
        List<File> validFiles = new ArrayList<>();
        List<String> missingRefs = new ArrayList<>();
        Map<String, ResourceResolutionResult> outsideRoot = new LinkedHashMap<>();
        Map<String, ResourceResolutionResult> fromDependencies = new LinkedHashMap<>();

        for (String path : paths) {
            ResourceResolutionResult result = resolveStrict(path, expectedRoot, projectRoot);

            switch (result.source()) {
                case DISK_IN_ROOT:
                    result.file().ifPresent(validFiles::add);
                    break;

                case CLASSPATH_DEPENDENCY:
                    result.file().ifPresent(validFiles::add);
                    fromDependencies.put(path, result);
                    break;

                case DISK_OUTSIDE_ROOT:
                case CLASSPATH_MATERIALIZED:
                    outsideRoot.put(path, result);
                    break;

                case NOT_FOUND:
                    missingRefs.add(path);
                    break;
            }
        }

        return new ResolvedResources(validFiles, missingRefs, outsideRoot, fromDependencies);
    }

    /**
     * Checks if file is located under the specified directory.
     *
     * @param file the file to check
     * @param directory the directory
     * @return true if file is under directory
     */
    public boolean isUnderDirectory(File file, File directory) {
        try {
            Path filePath = file.getCanonicalFile().toPath();
            Path dirPath = directory.getCanonicalFile().toPath();
            return filePath.startsWith(dirPath);
        } catch (IOException e) {
            return false;
        }
    }

    // Private helper methods

    private Optional<File> searchOnDisk(String normalizedPath, File resourceRoot) {
        File directFile = new File(resourceRoot, normalizedPath);
        if (directFile.isFile()) {
            return Optional.of(directFile);
        }
        return Optional.empty();
    }

    private Optional<DependencyResolution> searchInDependencies(String normalizedPath, File projectRoot) {
        try {
            String cacheKey = projectRoot.getCanonicalPath() + "::dep::" + normalizedPath;

            File cachedFile = materializedCache.getOrCreate(cacheKey, key -> {
                try {
                    ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
                    URL url = cl.getResource(normalizedPath);
                    if (url == null) {
                        return null;
                    }

                    String urlString = url.toString();
                    if (!urlString.startsWith("jar:file:")) {
                        return null;
                    }

                    String jarPath = urlString.substring("jar:file:".length(), urlString.indexOf("!"));
                    File jarFile = new File(jarPath);

                    if (!jarFile.getAbsolutePath().contains("target" + File.separator + "dependency")) {
                        return null;
                    }

                    Path tempRoot = Files.createTempDirectory("dsf-linter-dependency-");
                    return materializeResource(normalizedPath, url, tempRoot);

                } catch (Exception e) {
                    return null;
                }
            });

            if (cachedFile != null) {
                String jarName = extractJarName(normalizedPath, projectRoot);
                return Optional.of(new DependencyResolution(cachedFile, jarName));
            }

            return Optional.empty();

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private File materializeResource(String path, URL url, Path tempRoot) throws IOException {
        tempRoot.toFile().deleteOnExit();

        Path targetPath = tempRoot.resolve(path);
        Files.createDirectories(targetPath.getParent());

        try (InputStream in = url.openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        File result = targetPath.toFile();
        result.deleteOnExit();
        return result;
    }

    private String extractJarName(String path, File projectRoot) {
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            URL url = cl.getResource(path);
            if (url != null && url.toString().startsWith("jar:file:")) {
                String jarPath = url.toString().substring("jar:file:".length(), url.toString().indexOf("!"));
                return new File(jarPath).getName();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "unknown.jar";
    }

    /**
     * Internal record for dependency resolution results.
     */
    private record DependencyResolution(File file, String jarName) {}

    /**
     * Result container for multiple resource resolutions.
     */
    public record ResolvedResources(
            List<File> validFiles,
            List<String> missingRefs,
            Map<String, ResourceResolutionResult> outsideRoot,
            Map<String, ResourceResolutionResult> fromDependencies
    ) {}
}