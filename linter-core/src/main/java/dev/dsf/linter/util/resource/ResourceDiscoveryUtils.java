package dev.dsf.linter.util.resource;

import dev.dsf.linter.plugin.GenericPluginAdapter;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class containing shared resource discovery methods.
 * Enhanced with strict resource root linter and dependency JAR scanning.
 */
public final class ResourceDiscoveryUtils {

    private ResourceDiscoveryUtils() {
    }

    /**
     * Result container for resolved resources with legacy structure.
     */
    public record ResolvedResources(List<File> resolvedFiles, List<String> missingRefs) {}

    /**
     * Enhanced result container with resource root linter and dependency tracking.
     */
    public record StrictResolvedResources(
            List<File> lintFiles,
            List<String> missingRefs,
            Map<String, ResourceResolver.ResolutionResult> outsideRootFiles,
            Map<String, ResourceResolver.ResolutionResult> dependencyFiles
    ) {}

    /**
     * Collects all BPMN paths referenced by the plugin.
     */
    public static Set<String> collectBpmnPaths(PluginAdapter pluginAdapter) {
        return pluginAdapter.getProcessModels().stream()
                .map(ResourceResolver::normalizeRef)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Collects all FHIR paths referenced by the plugin.
     */
    public static Set<String> collectFhirPaths(PluginAdapter pluginAdapter) {
        return pluginAdapter.getFhirResourcesByProcessId().values().stream()
                .flatMap(Collection::stream)
                .map(ResourceResolver::normalizeRef)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Resolves resource files with strict linting against expected resource root.
     * Enhanced to search in dependency JARs if not found locally.
     * <p>
     * This method lints that all resolved files are within the expected resource root
     * or come from dependency JARs.
     * </p>
     *
     * @param referencedPaths the set of referenced resource paths
     * @param expectedResourceRoot the expected resource root directory for linting
     * @param projectRoot the project root for dependency JAR search
     * @return enhanced resolved resources with linting results
     */
    public static StrictResolvedResources resolveResourceFilesStrict(
            Set<String> referencedPaths,
            File expectedResourceRoot,
            File projectRoot) {

        List<File> lintFiles = new ArrayList<>();
        List<String> missingRefs = new ArrayList<>();
        Map<String, ResourceResolver.ResolutionResult> outsideRootFiles = new LinkedHashMap<>();
        Map<String, ResourceResolver.ResolutionResult> dependencyFiles = new LinkedHashMap<>();

        for (String ref : referencedPaths) {
            String cleanedRef = cleanRef(ref);
            ResourceResolver.ResolutionResult result =
                    ResourceResolver.resolveToFileStrict(cleanedRef, expectedResourceRoot, projectRoot);

            switch (result.source()) {
                case DISK_IN_ROOT:
                    result.file().ifPresent(lintFiles::add);
                    break;

                case CLASSPATH_DEPENDENCY:
                    // Found in dependency JAR - treat as valid but track separately
                    result.file().ifPresent(lintFiles::add);
                    dependencyFiles.put(ref, result);
                    break;

                case DISK_OUTSIDE_ROOT:
                case CLASSPATH_MATERIALIZED:
                    outsideRootFiles.put(ref, result);
                    break;

                case NOT_FOUND:
                    missingRefs.add(ref);
                    break;
            }
        }

        return new StrictResolvedResources(lintFiles, missingRefs, outsideRootFiles, dependencyFiles);
    }

    /**
     * Legacy method - resolves resource files without strict linting.
     * Kept for backward compatibility.
     *
     * @deprecated Use resolveResourceFilesStrict() for linting scenarios
     */
    @Deprecated
    public static ResolvedResources resolveResourceFiles(Set<String> referencedPaths, File resourcesDir) {
        List<File> resolvedFiles = referencedPaths.stream()
                .map(ResourceDiscoveryUtils::cleanRef)
                .map(ref -> ResourceResolver.resolveToFile(ref, resourcesDir))
                .flatMap(Optional::stream)
                .distinct()
                .collect(Collectors.toList());

        List<String> missingRefs = referencedPaths.stream()
                .map(ResourceDiscoveryUtils::cleanRef)
                .filter(ref -> ResourceResolver.resolveToFile(ref, resourcesDir).isEmpty())
                .distinct()
                .collect(Collectors.toList());

        return new ResolvedResources(resolvedFiles, missingRefs);
    }

    /**
     * Cleans and normalizes a resource reference.
     */
    public static String cleanRef(String ref) {
        if (ref == null) return "";
        String r = ref.trim();

        // Drop classpath: prefix
        if (r.startsWith("classpath:")) {
            r = r.substring("classpath:".length());
        }

        // Unify separators
        r = r.replace('\\', '/');

        // Remove all leading slashes
        while (r.startsWith("/")) {
            r = r.substring(1);
        }

        return r;
    }

    /**
     * Generates a unique name for a plugin to avoid directory conflicts.
     */
    public static String generateUniquePluginName(String baseName, PluginAdapter plugin,
                                                  int counter, Set<String> existingNames) {
        String name = baseName;

        if (counter > 0 || existingNames.contains(name)) {
            String version = getVersionSuffix(plugin);
            name = baseName + "_" + version;

            if (counter > 0 || existingNames.contains(name)) {
                name = name + "_" + (counter + 1);
            }
        }

        return name;
    }

    /**
     * Sanitizes a plugin name for use as directory name.
     */
    public static String sanitizePluginName(String name) {
        if (name == null || name.isEmpty()) {
            return "unnamed_plugin";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_{2,}", "_")
                .toLowerCase();
    }

    /**
     * Extracts version suffix from a plugin adapter.
     */
    private static String getVersionSuffix(PluginAdapter plugin) {
        if (plugin instanceof GenericPluginAdapter generic) {
            return generic.getApiVersion() == GenericPluginAdapter.ApiVersion.V2 ? "v2" : "v1";
        }
        return "v1";
    }
}