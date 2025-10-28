package dev.dsf.linter.util.loader;

import dev.dsf.linter.plugin.GenericPluginAdapter;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static dev.dsf.linter.constants.DsfApiConstants.V1_PLUGIN_INTERFACE;
import static dev.dsf.linter.constants.DsfApiConstants.V2_PLUGIN_INTERFACE;

public final class ServiceLoaderUtils {

    private ServiceLoaderUtils() {
        // Utility class
    }

    /**
     * Discovers plugins using ServiceLoader for both v1 and v2 interfaces.
     * Always tries V2 first, then V1.
     *
     * @param classLoader the classloader to use for discovery
     * @return list of discovered plugin adapters
     */
    public static List<PluginDefinitionDiscovery.PluginAdapter> discoverPluginsViaServiceLoader(ClassLoader classLoader) {
        List<PluginDefinitionDiscovery.PluginAdapter> plugins = new ArrayList<>();

        // Try v2 first (prefer v2 if both exist)
        try {
            Class<?> v2Class = Class.forName(V2_PLUGIN_INTERFACE, false, classLoader);
            ServiceLoader<?> v2Loader = ServiceLoader.load(v2Class, classLoader);
            for (Object instance : v2Loader) {
                plugins.add(new GenericPluginAdapter(instance, GenericPluginAdapter.ApiVersion.V2));
            }
        } catch (ClassNotFoundException ignored) {
            // V2 not available
        }

        // Try v1
        try {
            Class<?> v1Class = Class.forName(V1_PLUGIN_INTERFACE, false, classLoader);
            ServiceLoader<?> v1Loader = ServiceLoader.load(v1Class, classLoader);
            for (Object instance : v1Loader) {
                plugins.add(new GenericPluginAdapter(instance, GenericPluginAdapter.ApiVersion.V1));
            }
        } catch (ClassNotFoundException ignored) {
            // V1 not available
        }

        return plugins;
    }
}