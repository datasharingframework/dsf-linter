package dev.dsf.linter.util.loader;

import dev.dsf.linter.plugin.PluginDefinitionDiscovery;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.V1Adapter;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.V2Adapter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Function;

import static dev.dsf.linter.constants.DsfApiConstants.V1_PLUGIN_INTERFACE;
import static dev.dsf.linter.constants.DsfApiConstants.V2_PLUGIN_INTERFACE;

/**
 * Utility class for ServiceLoader-based plugin discovery.
 * Supports both v1 and v2 ProcessPluginDefinition interfaces.
 */
public final class ServiceLoaderUtils {

    /** Safety net: ServiceLoader cannot guarantee that the iterator advances after an error. */
    private static final int MAX_PROVIDER_FAILURES = 100;

    private ServiceLoaderUtils() {
    }

    /**
     * Discovers plugins using ServiceLoader for both v1 and v2 interfaces.
     * Always tries V2 first, then V1 to prefer newer implementations.
     * Broken service registrations are ignored (see {@link #discoverPluginsViaServiceLoader(ClassLoader, List)}).
     *
     * @param classLoader the classloader to use for discovery
     * @return list of discovered plugin adapters (V2 first, then V1)
     */
    public static List<PluginDefinitionDiscovery.PluginAdapter> discoverPluginsViaServiceLoader(ClassLoader classLoader) {
        return discoverPluginsViaServiceLoader(classLoader, new ArrayList<>());
    }

    /**
     * Same as {@link #discoverPluginsViaServiceLoader(ClassLoader)}, but a broken service registration
     * (e.g. a provider that does not implement the interface it is registered for) does not abort discovery.
     * The {@link ServiceConfigurationError} message is added to {@code errors} instead.
     *
     * @param classLoader the classloader to use for discovery
     * @param errors      receives one message per broken registration
     * @return list of discovered plugin adapters (V2 first, then V1)
     */
    public static List<PluginDefinitionDiscovery.PluginAdapter> discoverPluginsViaServiceLoader(
            ClassLoader classLoader, List<String> errors) {
        List<PluginDefinitionDiscovery.PluginAdapter> plugins = new ArrayList<>();

        plugins.addAll(load(V2_PLUGIN_INTERFACE, classLoader, V2Adapter::new, errors));
        plugins.addAll(load(V1_PLUGIN_INTERFACE, classLoader, V1Adapter::new, errors));

        return plugins;
    }

    /**
     * Loads plugins of one API version using ServiceLoader.
     */
    private static List<PluginDefinitionDiscovery.PluginAdapter> load(
            String interfaceName, ClassLoader classLoader,
            Function<Object, PluginDefinitionDiscovery.PluginAdapter> adapter, List<String> errors) {
        List<PluginDefinitionDiscovery.PluginAdapter> plugins = new ArrayList<>();

        try {
            Class<?> pluginInterface = Class.forName(interfaceName, false, classLoader);
            Iterator<?> providers = ServiceLoader.load(pluginInterface, classLoader).iterator();

            int failures = 0;
            while (failures < MAX_PROVIDER_FAILURES) {
                try {
                    if (!providers.hasNext()) {
                        break;
                    }
                    plugins.add(adapter.apply(providers.next()));
                } catch (ServiceConfigurationError e) {
                    // Wrong/missing provider class: report it, keep going with the remaining providers.
                    errors.add(e.getMessage());
                    failures++;
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Interface not available on classpath
        }

        return plugins;
    }
}
