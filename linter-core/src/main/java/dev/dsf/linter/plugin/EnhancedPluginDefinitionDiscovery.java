package dev.dsf.linter.plugin;

import dev.dsf.linter.plugin.PluginDefinitionDiscovery.V1Adapter;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.V2Adapter;
import dev.dsf.linter.util.api.PluginVersionUtils;
import dev.dsf.linter.util.loader.ServiceLoaderUtils;
import dev.dsf.linter.util.resource.ResourceDiscoveryUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static dev.dsf.linter.classloading.ClassInspector.logger;

/**
 * Plugin discovery that supports any number of ProcessPluginDefinition implementations.
 * A single plugin is simply a special case with one entry in the result.
 * Leverages the public methods from PluginDefinitionDiscovery to avoid code duplication.
 */
public final class EnhancedPluginDefinitionDiscovery {

    /**
     * Result container for plugin discovery.
     */
    public static class DiscoveryResult {
        private final List<PluginDefinitionDiscovery.PluginAdapter> v1Plugins;
        private final List<PluginDefinitionDiscovery.PluginAdapter> v2Plugins;
        private final Map<String, List<PluginDefinitionDiscovery.PluginAdapter>> pluginsByName;

        public DiscoveryResult(List<PluginDefinitionDiscovery.PluginAdapter> plugins) {
            this.v1Plugins = plugins.stream()
                    .filter(p -> p instanceof V1Adapter)
                    .collect(Collectors.toList());

            this.v2Plugins = plugins.stream()
                    .filter(p -> p instanceof V2Adapter)
                    .collect(Collectors.toList());

            // Group by plugin name for report structure
            this.pluginsByName = plugins.stream()
                    .collect(Collectors.groupingBy(
                            p -> ResourceDiscoveryUtils.sanitizePluginName(p.getName()),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
        }

        /**
         * Returns all discovered plugins with V2 first, then V1.
         */
        public List<PluginDefinitionDiscovery.PluginAdapter> getAllPlugins() {
            List<PluginDefinitionDiscovery.PluginAdapter> all = new ArrayList<>();
            all.addAll(v2Plugins);
            all.addAll(v1Plugins);
            return all;
        }

        /**
         * Returns plugins grouped by sanitized name.
         */
        public Map<String, List<PluginDefinitionDiscovery.PluginAdapter>> getPluginsByName() {
            return pluginsByName;
        }

    }

    /**
     * Discovers ALL plugin definitions from classpath or project root.
     * Works uniformly for any number of plugins (one or more).
     * Uses the public methods from PluginDefinitionDiscovery to collect all plugins.
     *
     * @param projectRoot optional project root directory for scanning
     * @return discovery result containing all found plugins
     */
    public static DiscoveryResult discoverAll(File projectRoot) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = EnhancedPluginDefinitionDiscovery.class.getClassLoader();
        }

        // Step 1: Try with ServiceLoader (finds all registered plugins)
        List<PluginDefinitionDiscovery.PluginAdapter> allCandidates =
                new ArrayList<>(ServiceLoaderUtils.discoverPluginsViaServiceLoader(cl));

        // Debug output for ServiceLoader results
        if (!allCandidates.isEmpty()) {
            logger.debug("[DEBUG] Found " + allCandidates.size() + " plugin(s) via ServiceLoader");
            allCandidates.forEach(p -> {
                logger.debug("  -> Plugin: " + p.getName() + " (" + p.sourceClass().getName() + ")");
                logger.debug("     Version: " + PluginVersionUtils.getVersionSuffix(p));
            });
        }

        // Step 2: If ServiceLoader found nothing, use the public scan methods from PluginDefinitionDiscovery
        if (allCandidates.isEmpty()) {
            logger.debug("[DEBUG] ServiceLoader found nothing. Starting manual scan...");

            // Use the now-public methods from PluginDefinitionDiscovery
            allCandidates.addAll(PluginDefinitionDiscovery.scanJars(cl));
            allCandidates.addAll(PluginDefinitionDiscovery.scanDirectories(cl));

            if (projectRoot != null) {
                allCandidates.addAll(PluginDefinitionDiscovery.scanProjectRoot(projectRoot));
            }
        }

        // Step 3: Deduplicate by class name
        Map<String, PluginDefinitionDiscovery.PluginAdapter> uniquePlugins = new LinkedHashMap<>();
        for (PluginDefinitionDiscovery.PluginAdapter adapter : allCandidates) {
            String className = adapter.sourceClass().getName();
            uniquePlugins.putIfAbsent(className, adapter);
        }

        List<PluginDefinitionDiscovery.PluginAdapter> finalPlugins =
                new ArrayList<>(uniquePlugins.values());

        logger.debug("Total unique plugins discovered: " + finalPlugins.size());

        return new DiscoveryResult(finalPlugins);
    }
}