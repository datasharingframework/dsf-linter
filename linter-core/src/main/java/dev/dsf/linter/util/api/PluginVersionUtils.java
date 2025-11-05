package dev.dsf.linter.util.api;

import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.V1Adapter;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.V2Adapter;

/**
 * Utility class for determining API versions from plugin adapters.
 * Centralizes version detection logic to avoid duplication across the codebase.
 */
public final class PluginVersionUtils {

    private PluginVersionUtils() {
    }

    /**
     * Detects the API version from a plugin adapter using instanceof checks.
     *
     * @param adapter the plugin adapter to check
     * @return the detected API version (V1, V2, or UNKNOWN)
     */
    public static ApiVersion detectApiVersion(PluginAdapter adapter) {
        if (adapter instanceof V2Adapter) {
            return ApiVersion.V2;
        } else if (adapter instanceof V1Adapter) {
            return ApiVersion.V1;
        }
        return ApiVersion.UNKNOWN;
    }

    /**
     * Returns a version suffix string for directory naming purposes.
     *
     * @param adapter the plugin adapter
     * @return "v2", "v1", or "unknown"
     */
    public static String getVersionSuffix(PluginAdapter adapter) {
        return switch (detectApiVersion(adapter)) {
            case V2 -> "v2";
            case V1 -> "v1";
            case UNKNOWN -> "unknown";
        };
    }
}