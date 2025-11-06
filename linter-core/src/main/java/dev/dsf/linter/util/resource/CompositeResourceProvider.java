package dev.dsf.linter.util.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

/**
 * Generic composite provider that queries multiple providers in order.
 * Eliminates duplication between BPMN and FHIR composite providers.
 *
 * @param <T> the type of resource entry (BpmnResourceEntry or FhirResourceEntry)
 */
public record CompositeResourceProvider<T>(List<ResourceProvider<T>> providers,
                                           String resourceTypeName) implements ResourceProvider<T> {

    public CompositeResourceProvider(List<ResourceProvider<T>> providers, String resourceTypeName) {
        Objects.requireNonNull(providers, "providers cannot be null");
        Objects.requireNonNull(resourceTypeName, "resourceTypeName cannot be null");

        if (providers.isEmpty()) {
            throw new IllegalArgumentException("At least one provider must be specified");
        }

        this.providers = List.copyOf(providers);
        this.resourceTypeName = resourceTypeName;
    }

    @SafeVarargs
    public CompositeResourceProvider(String resourceTypeName, ResourceProvider<T>... providers) {
        this(Arrays.asList(providers), resourceTypeName);
    }

    @Override
    public Stream<T> listResources(String directory) {
        return providers.stream()
                .flatMap(provider -> provider.listResources(directory))
                .distinct();
    }

    @Override
    public InputStream openResource(String path) throws IOException {
        for (ResourceProvider<T> provider : providers) {
            if (provider.resourceExists(path)) {
                return provider.openResource(path);
            }
        }

        throw new IOException("Resource not found in any provider: " + path);
    }

    @Override
    public boolean resourceExists(String path) {
        return providers.stream()
                .anyMatch(provider -> provider.resourceExists(path));
    }

    @Override
    public String getDescription() {
        return String.format("Composite[%s: %d providers]",
                resourceTypeName,
                providers.size());
    }
}