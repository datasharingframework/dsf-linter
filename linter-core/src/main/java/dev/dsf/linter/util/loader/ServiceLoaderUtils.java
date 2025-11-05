package dev.dsf.linter.util.loader;

import dev.dsf.linter.plugin.PluginDefinitionDiscovery;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.V1Adapter;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.V2Adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static dev.dsf.linter.constants.DsfApiConstants.V1_PLUGIN_INTERFACE;
import static dev.dsf.linter.constants.DsfApiConstants.V2_PLUGIN_INTERFACE;

/**
 * Utility class for ServiceLoader-based plugin discovery.
 * Supports both v1 and v2 ProcessPluginDefinition interfaces.
 */
public final class ServiceLoaderUtils {

    private ServiceLoaderUtils() {
    }

    /**
     * Discovers plugins using ServiceLoader for both v1 and v2 interfaces.
     * Always tries V2 first, then V1 to prefer newer implementations.
     *
     * @param classLoader the classloader to use for discovery
     * @return list of discovered plugin adapters (V2 first, then V1)
     */
    public static List<PluginDefinitionDiscovery.PluginAdapter> discoverPluginsViaServiceLoader(ClassLoader classLoader) {
        List<PluginDefinitionDiscovery.PluginAdapter> plugins = new ArrayList<>();

        plugins.addAll(loadV2Plugins(classLoader));
        plugins.addAll(loadV1Plugins(classLoader));

        return plugins;
    }

    /**
     * Loads v2 plugins using ServiceLoader.
     *
     * @param classLoader the classloader to use
     * @return list of v2 plugin adapters
     */
    private static List<PluginDefinitionDiscovery.PluginAdapter> loadV2Plugins(ClassLoader classLoader) {
        List<PluginDefinitionDiscovery.PluginAdapter> plugins = new ArrayList<>();

        try {
            Class<?> v2Class = Class.forName(V2_PLUGIN_INTERFACE, false, classLoader);
            ServiceLoader<?> v2Loader = ServiceLoader.load(v2Class, classLoader);

            for (Object instance : v2Loader) {
                plugins.add(new V2Adapter(instance));
            }
        } catch (ClassNotFoundException ignored) {
            // V2 interface not available on classpath
        }

        return plugins;
    }

    /**
     * Loads v1 plugins using ServiceLoader.
     *
     * @param classLoader the classloader to use
     * @return list of v1 plugin adapters
     */
    private static List<PluginDefinitionDiscovery.PluginAdapter> loadV1Plugins(ClassLoader classLoader) {
        List<PluginDefinitionDiscovery.PluginAdapter> plugins = new ArrayList<>();

        try {
            Class<?> v1Class = Class.forName(V1_PLUGIN_INTERFACE, false, classLoader);
            ServiceLoader<?> v1Loader = ServiceLoader.load(v1Class, classLoader);

            for (Object instance : v1Loader) {
                plugins.add(new V1Adapter(instance));
            }
        } catch (ClassNotFoundException ignored) {
            // V1 interface not available on classpath
        }

        return plugins;
    }
}