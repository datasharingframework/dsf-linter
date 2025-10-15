package dev.dsf.linter.plugin;

import dev.dsf.linter.util.resource.ResourceDiscoveryUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

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
            this.v1Plugins = plugins.stream()
                    .filter(p -> p instanceof PluginDefinitionDiscovery.V1Adapter)
                    .collect(Collectors.toList());

            this.v2Plugins = plugins.stream()
                    .filter(p -> p instanceof PluginDefinitionDiscovery.V2Adapter)
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

        public List<PluginDefinitionDiscovery.PluginAdapter> getV1Plugins() { return v1Plugins; }
        public List<PluginDefinitionDiscovery.PluginAdapter> getV2Plugins() { return v2Plugins; }
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
            System.out.println("[DEBUG] Found " + allCandidates.size() + " plugin(s) via ServiceLoader");
            allCandidates.forEach(p -> {
                System.out.println("  -> Plugin: " + p.getName() + " (" + p.sourceClass().getName() + ")");
                System.out.println("     Version: " + (p instanceof PluginDefinitionDiscovery.V2Adapter ? "v2" : "v1"));
            });
        }

        // Step 2: If ServiceLoader found nothing, use the public scan methods from PluginDefinitionDiscovery
        if (allCandidates.isEmpty()) {
            System.out.println("[DEBUG] ServiceLoader found nothing. Starting manual scan...");

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
            Class<?> v2Class = Class.forName("dev.dsf.bpe.v2.ProcessPluginDefinition", false, cl);
            ServiceLoader<?> v2Loader = ServiceLoader.load(v2Class, cl);
            for (Object instance : v2Loader) {
                plugins.add(new PluginDefinitionDiscovery.V2Adapter(instance));
            }
        } catch (ClassNotFoundException ignored) {}

        // Try v1
        try {
            Class<?> v1Class = Class.forName("dev.dsf.bpe.v1.ProcessPluginDefinition", false, cl);
            ServiceLoader<?> v1Loader = ServiceLoader.load(v1Class, cl);
            for (Object instance : v1Loader) {
                plugins.add(new PluginDefinitionDiscovery.V1Adapter(instance));
            }
        } catch (ClassNotFoundException ignored) {}

        return plugins;
    }
}