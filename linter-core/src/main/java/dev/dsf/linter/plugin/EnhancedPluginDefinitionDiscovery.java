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
 * <p>
 * For extracted JAR structures, this discovery:
 * <ol>
 *   <li>First tries ServiceLoader discovery (reads META-INF/services/)</li>
 *   <li>If nothing found and projectRoot is provided, scans the project root directly</li>
 *   <li>Deduplicates plugins by class name</li>
 * </ol>
 * </p>
 * <p>
 * Supports partial success: Valid plugins are processed even if some fail.
 * </p>
 */
public final class EnhancedPluginDefinitionDiscovery {

    /**
     * Result container for plugin discovery with support for partial success.
     */
    public static class DiscoveryResult {
        private final List<PluginDefinitionDiscovery.PluginAdapter> v1Plugins;
        private final List<PluginDefinitionDiscovery.PluginAdapter> v2Plugins;
        private final Map<String, List<PluginDefinitionDiscovery.PluginAdapter>> pluginsByName;
        private final List<PluginDiscoveryError> failedPlugins;

        public DiscoveryResult(List<PluginDefinitionDiscovery.PluginAdapter> plugins,
                               List<PluginDiscoveryError> failedPlugins) {
            this.v1Plugins = plugins.stream()
                    .filter(p -> p instanceof V1Adapter)
                    .collect(Collectors.toList());

            this.v2Plugins = plugins.stream()
                    .filter(p -> p instanceof V2Adapter)
                    .collect(Collectors.toList());

            this.pluginsByName = plugins.stream()
                    .collect(Collectors.groupingBy(
                            p -> ResourceDiscoveryUtils.sanitizePluginName(p.getName()),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            this.failedPlugins = new ArrayList<>(failedPlugins);
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

        /**
         * Returns failed plugin discoveries.
         */
        public List<PluginDiscoveryError> getFailedPlugins() {
            return failedPlugins;
        }

        /**
         * Returns true if any plugins failed discovery.
         */
        public boolean hasFailedPlugins() {
            return !failedPlugins.isEmpty();
        }

    }

    /**
     * Discovers ALL plugin definitions from extracted JAR structure.
     * <p>
     * Discovery strategy:
     * <ol>
     *   <li>Try ServiceLoader (META-INF/services/)</li>
     *   <li>If nothing found and projectRoot provided, scan project root directly</li>
     *   <li>Deduplicate by class name</li>
     * </ol>
     * </p>
     * <p>
     * Supports partial success: Valid plugins are processed even if some fail.
     * </p>
     *
     * @param projectRoot the project root directory (extracted JAR root)
     * @return discovery result containing all found plugins and any errors
     */
    public static DiscoveryResult discoverAll(File projectRoot) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = EnhancedPluginDefinitionDiscovery.class.getClassLoader();
        }

        PluginDefinitionDiscovery.DiscoveryContext context = new PluginDefinitionDiscovery.DiscoveryContext();

        List<PluginDefinitionDiscovery.PluginAdapter> serviceLoaderPlugins =
                new ArrayList<>(ServiceLoaderUtils.discoverPluginsViaServiceLoader(cl));

        if (!serviceLoaderPlugins.isEmpty()) {
            logger.debug("[DEBUG] Found " + serviceLoaderPlugins.size() + " plugin(s) via ServiceLoader");
            serviceLoaderPlugins.forEach(p -> {
                logger.debug("  -> Plugin: " + p.getName() + " (" + p.sourceClass().getName() + ")");
                logger.debug("     Version: " + PluginVersionUtils.getVersionSuffix(p));
            });
            serviceLoaderPlugins.forEach(context::addSuccess);
        }

        if (serviceLoaderPlugins.isEmpty() && projectRoot != null) {
            logger.debug("[DEBUG] ServiceLoader found nothing. Scanning project root...");
            PluginDefinitionDiscovery.scanProjectRoot(projectRoot, context);
        }

        Map<String, PluginDefinitionDiscovery.PluginAdapter> uniquePlugins = new LinkedHashMap<>();
        for (PluginDefinitionDiscovery.PluginAdapter adapter : context.getSuccessfulPlugins()) {
            String className = adapter.sourceClass().getName();
            uniquePlugins.putIfAbsent(className, adapter);
        }

        List<PluginDefinitionDiscovery.PluginAdapter> finalPlugins =
                new ArrayList<>(uniquePlugins.values());

        logger.debug("Total unique plugins discovered: " + finalPlugins.size());

        if (context.getFailedPlugins().isEmpty()) {
            logger.debug("No plugin discovery failures");
        } else {
            logger.warn("Failed to discover " + context.getFailedPlugins().size() + " plugin(s)");
        }

        return new DiscoveryResult(finalPlugins, context.getFailedPlugins());
    }
}