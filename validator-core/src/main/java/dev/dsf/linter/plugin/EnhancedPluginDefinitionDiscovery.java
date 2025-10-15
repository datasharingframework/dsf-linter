package dev.dsf.linter.plugin;

import dev.dsf.linter.util.resource.ResourceDiscoveryUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static dev.dsf.linter.classloading.ClassInspector.logger;
import static dev.dsf.linter.constants.DsfApiConstants.V1_PLUGIN_INTERFACE;
import static dev.dsf.linter.constants.DsfApiConstants.V2_PLUGIN_INTERFACE;

/**
 * Plugin discovery that supports any number of ProcessPluginDefinition implementations.
 * A single plugin is simply a special case with one entry in the result.
 * Leverages the public methods from PluginDefinitionDiscovery to avoid code duplication.
 */
public final class EnhancedPluginDefinitionDiscovery {

    /**
     * Result container for plugin discovery
     */
    public static class DiscoveryResult {
        private final List<PluginDefinitionDiscovery.PluginAdapter> v1Plugins;
        private final List<PluginDefinitionDiscovery.PluginAdapter> v2Plugins;
        private final Map<String, List<PluginDefinitionDiscovery.PluginAdapter>> pluginsByName;

        public DiscoveryResult(List<PluginDefinitionDiscovery.PluginAdapter> plugins) {
            // Filter v1 and v2 plugins based on GenericPluginAdapter version
            this.v1Plugins = plugins.stream()
                    .filter(p -> p instanceof GenericPluginAdapter)
                    .map(p -> (GenericPluginAdapter) p)
                    .filter(p -> p.getApiVersion() == GenericPluginAdapter.ApiVersion.V1)
                    .collect(Collectors.toList());

            this.v2Plugins = plugins.stream()
                    .filter(p -> p instanceof GenericPluginAdapter)
                    .map(p -> (GenericPluginAdapter) p)
                    .filter(p -> p.getApiVersion() == GenericPluginAdapter.ApiVersion.V2)
                    .collect(Collectors.toList());

            // Group by plugin name for report structure
            this.pluginsByName = plugins.stream()
                    .collect(Collectors.groupingBy(
                            p -> ResourceDiscoveryUtils.sanitizePluginName(p.getName()),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
        }

        public List<PluginDefinitionDiscovery.PluginAdapter> getAllPlugins() {
            List<PluginDefinitionDiscovery.PluginAdapter> all = new ArrayList<>();
            all.addAll(v2Plugins);
            all.addAll(v1Plugins);
            return all;
        }

        public Map<String, List<PluginDefinitionDiscovery.PluginAdapter>> getPluginsByName() { return pluginsByName; }
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
        if (cl == null) cl = EnhancedPluginDefinitionDiscovery.class.getClassLoader();

        // Step 1: Try with ServiceLoader (finds all registered plugins)
        List<PluginDefinitionDiscovery.PluginAdapter> allCandidates = new ArrayList<>(discoverViaServiceLoader(cl));

        // Debug output for ServiceLoader results
        if (!allCandidates.isEmpty()) {
            logger.debug("[DEBUG] Found " + allCandidates.size() + " plugin(s) via ServiceLoader");
            allCandidates.forEach(p -> {
                logger.debug("  -> Plugin: " + p.getName() + " (" + p.sourceClass().getName() + ")");

                // Determine version from GenericPluginAdapter
                String version = "unknown";
                if (p instanceof GenericPluginAdapter generic) {
                    version = generic.getApiVersion() == GenericPluginAdapter.ApiVersion.V2 ? "v2" : "v1";
                }
                logger.debug("     Version: " + version);
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
            if (!uniquePlugins.containsKey(className)) {
                uniquePlugins.put(className, adapter);
            }
        }

        List<PluginDefinitionDiscovery.PluginAdapter> finalPlugins = new ArrayList<>(uniquePlugins.values());
        System.out.println("[DEBUG] Total unique plugins discovered: " + finalPlugins.size());

        return new DiscoveryResult(finalPlugins);
    }

    /**
     * Discovers plugins using ServiceLoader for both v1 and v2.
     * Collects ALL plugins instead of stopping at the first one.
     */

    private static List<PluginDefinitionDiscovery.PluginAdapter> discoverViaServiceLoader(ClassLoader cl) {
        List<PluginDefinitionDiscovery.PluginAdapter> plugins = new ArrayList<>();

        // Try v2
        try {
            Class<?> v2Class = Class.forName(V2_PLUGIN_INTERFACE, false, cl);
            ServiceLoader<?> v2Loader = ServiceLoader.load(v2Class, cl);
            for (Object instance : v2Loader) {
                plugins.add(new GenericPluginAdapter(instance, GenericPluginAdapter.ApiVersion.V2));
            }
        } catch (ClassNotFoundException ignored) {}

        // Try v1
        try {
            Class<?> v1Class = Class.forName(V1_PLUGIN_INTERFACE, false, cl);
            ServiceLoader<?> v1Loader = ServiceLoader.load(v1Class, cl);
            for (Object instance : v1Loader) {
                plugins.add(new GenericPluginAdapter(instance, GenericPluginAdapter.ApiVersion.V1));
            }
        } catch (ClassNotFoundException ignored) {}

        return plugins;
    }
}