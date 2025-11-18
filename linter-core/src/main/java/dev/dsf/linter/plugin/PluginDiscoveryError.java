package dev.dsf.linter.plugin;

/**
 * Represents an error that occurred during plugin discovery.
 * Used to collect failed plugin discoveries without stopping the entire process.
 */
public record PluginDiscoveryError(
        String pluginClassName,
        String errorMessage,
        ErrorType errorType,
        String location
) {

    public enum ErrorType {
        INVALID_API_VERSION,
        MISSING_METHODS,
        INSTANTIATION_FAILED,
        CLASS_LOADING_FAILED
    }

    /**
     * Creates an error for invalid API version (neither V1 nor V2).
     */
    public static PluginDiscoveryError invalidApiVersion(String className, String location) {
        return new PluginDiscoveryError(
                className,
                "Plugin does not implement valid DSF API interface (neither v1 nor v2)",
                ErrorType.INVALID_API_VERSION,
                location
        );
    }

    /**
     * Creates an error for missing required methods.
     */
    public static PluginDiscoveryError missingMethods(String className, String location) {
        return new PluginDiscoveryError(
                className,
                "Plugin implements ProcessPluginDefinition but is missing required methods",
                ErrorType.MISSING_METHODS,
                location
        );
    }

    /**
     * Creates an error for instantiation failure.
     */
    public static PluginDiscoveryError instantiationFailed(String className, String location, Throwable cause) {
        return new PluginDiscoveryError(
                className,
                "Failed to instantiate plugin: " + cause.getMessage(),
                ErrorType.INSTANTIATION_FAILED,
                location
        );
    }

    /**
     * Creates an error for class loading failure.
     */
    public static PluginDiscoveryError classLoadingFailed(String className, String location, Throwable cause) {
        return new PluginDiscoveryError(
                className,
                "Failed to load plugin class: " + cause.getMessage(),
                ErrorType.CLASS_LOADING_FAILED,
                location
        );
    }
}