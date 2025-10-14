package dev.dsf.utils.validator.util.resource;

import dev.dsf.utils.validator.plugin.PluginDefinitionDiscovery.PluginAdapter;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class containing shared resource discovery methods.
 * Eliminates code duplication between ResourceDiscoveryService and EnhancedResourceDiscoveryService.
 */
public final class ResourceDiscoveryUtils {

    private ResourceDiscoveryUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Result container for resolved resources
     */
    public record ResolvedResources(List<File> resolvedFiles, List<String> missingRefs) {}

    /**
     * Collects all BPMN paths referenced by the plugin.
     *
     * @param pluginAdapter the plugin adapter
     * @return set of normalized BPMN resource paths
     */
    public static Set<String> collectBpmnPaths(PluginAdapter pluginAdapter) {
        return pluginAdapter.getProcessModels().stream()
                .map(ResourceResolver::normalizeRef)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Collects all FHIR paths referenced by the plugin.
     *
     * @param pluginAdapter the plugin adapter
     * @return set of normalized FHIR resource paths
     */
    public static Set<String> collectFhirPaths(PluginAdapter pluginAdapter) {
        return pluginAdapter.getFhirResourcesByProcessId().values().stream()
                .flatMap(Collection::stream)
                .map(ResourceResolver::normalizeRef)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Resolves resource files from their reference paths.
     *
     * @param referencedPaths the set of referenced resource paths
     * @param resourcesDir the resources directory to search in
     * @return resolved resources with files found and missing references
     */
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
     * Removes classpath: prefix, normalizes separators, and removes leading slashes.
     *
     * @param ref the reference to clean
     * @return cleaned reference string
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
     *
     * @param baseName the base name from the plugin
     * @param plugin the plugin adapter
     * @param counter counter for multiple plugins with same name
     * @param existingNames set of already used names
     * @return unique plugin name suitable for directory creation
     */
    public static String generateUniquePluginName(String baseName, PluginAdapter plugin,
                                                  int counter, Set<String> existingNames) {
        String name = baseName;

        // Add version suffix if needed
        if (counter > 0 || existingNames.contains(name)) {
            String version = (plugin instanceof dev.dsf.utils.validator.plugin.PluginDefinitionDiscovery.V2Adapter)
                    ? "v2" : "v1";
            name = baseName + "_" + version;

            if (counter > 0 || existingNames.contains(name)) {
                name = name + "_" + (counter + 1);
            }
        }

        return name;
    }

    /**
     * Sanitizes a plugin name for use as directory name.
     * Removes invalid filesystem characters.
     *
     * @param name the plugin name to sanitize
     * @return sanitized name suitable for filesystem use
     */
    public static String sanitizePluginName(String name) {
        if (name == null || name.isEmpty()) {
            return "unnamed_plugin";
        }
        // Remove invalid filesystem characters
        return name.replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_{2,}", "_")
                .toLowerCase();
    }
}