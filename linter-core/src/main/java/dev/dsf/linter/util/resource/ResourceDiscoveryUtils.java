package dev.dsf.linter.util.resource;

import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;
import dev.dsf.linter.util.api.PluginVersionUtils;

import java.io.File;
import java.util.*;

/**
 * Utility class containing shared resource discovery methods.
 * Enhanced with strict resource root linter and dependency JAR scanning.
 */
public final class ResourceDiscoveryUtils {


    /**
     * Enhanced result container with resource root linter and dependency tracking.
     */
    public record StrictResolvedResources(
            List<File> lintFiles,
            List<String> missingRefs,
            Map<String, ResourceResolutionResult> outsideRootFiles,
            Map<String, ResourceResolutionResult> dependencyFiles
    ) {}


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

        ResourceResolutionService resolutionService = new ResourceResolutionService();
        ResourceResolutionService.ResolvedResources resolved = 
                resolutionService.resolveMultiple(referencedPaths, expectedResourceRoot, projectRoot);

        return new StrictResolvedResources(
                resolved.validFiles(),
                resolved.missingRefs(),
                resolved.outsideRoot(),
                resolved.fromDependencies()
        );
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
        return PluginVersionUtils.getVersionSuffix(plugin);
    }
}