package dev.dsf.linter.plugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Generic adapter for ProcessPluginDefinition implementations.
 * Works with both v1 and v2 API versions using reflection.
 * Replaces the formerly duplicated V1Adapter and V2Adapter classes.
 */
public final class GenericPluginAdapter implements PluginDefinitionDiscovery.PluginAdapter {

    private final Object delegate;
    private final Class<?> delegateClass;
    private final ApiVersion apiVersion;

    /**
     * Creates a generic adapter wrapping a plugin instance.
     *
     * @param delegate the plugin instance (v1 or v2)
     * @param apiVersion the API version of the plugin
     */
    public GenericPluginAdapter(Object delegate, ApiVersion apiVersion) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (apiVersion == null) {
            throw new IllegalArgumentException("apiVersion must not be null");
        }

        this.delegate = delegate;
        this.delegateClass = delegate.getClass();
        this.apiVersion = apiVersion;
    }

    @Override
    public String getName() {
        return invokeMethod("getName", String.class, "");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getProcessModels() {
        return invokeMethod("getProcessModels", List.class, Collections.emptyList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<String>> getFhirResourcesByProcessId() {
        return invokeMethod("getFhirResourcesByProcessId", Map.class, Collections.emptyMap());
    }

    @Override
    public Class<?> sourceClass() {
        return delegateClass;
    }

    /**
     * Returns the API version of this plugin.
     *
     * @return API version (V1 or V2)
     */
    public ApiVersion getApiVersion() {
        return apiVersion;
    }

    /**
     * Generic reflection-based method invoker.
     *
     * @param methodName name of the method to invoke
     * @param returnType expected return type
     * @param defaultValue default value if method returns null
     * @param <T> return type
     * @return method result or default value
     * @throws RuntimeException if invocation fails
     */
    private <T> T invokeMethod(String methodName, Class<T> returnType, T defaultValue) {
        try {
            @SuppressWarnings("unchecked")
            T result = (T) delegateClass.getMethod(methodName).invoke(delegate);
            return result != null ? result : defaultValue;
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("Failed to invoke %s() on %s", methodName, delegateClass.getName()),
                    e
            );
        }
    }

    @Override
    public String toString() {
        return String.format("GenericPluginAdapter[name=%s, version=%s, class=%s]",
                getName(), apiVersion, delegateClass.getSimpleName());
    }

    /**
     * API version enum for distinguishing v1 and v2 plugins.
     */
    public enum ApiVersion {
        V1, V2
    }
}