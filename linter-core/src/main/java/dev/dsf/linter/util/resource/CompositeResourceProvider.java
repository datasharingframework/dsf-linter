package dev.dsf.linter.util.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

/**
 * Composite implementation of {@link ResourceProvider} that combines multiple providers
 * into a single unified provider.
 * <p>
 * This class implements the Composite Pattern, allowing transparent access to resources
 * from multiple sources (e.g., filesystem and JAR files) as if they were a single provider.
 * Resources are searched across all providers in the order they were specified.
 * </p>
 * <p>
 * When listing resources, results from all providers are combined and duplicates are removed.
 * When opening a resource, the first provider that contains the resource is used.
 * </p>
 *
 * @param <T> the type of resource entry (e.g., FhirResourceEntry)
 * @since 1.1.0
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
        IOException lastException = null;

        for (ResourceProvider<T> provider : providers) {
            try {
                return provider.openResource(path);
            } catch (IOException e) {
                // Store exception but continue to next provider
                // Only throw if this is the last provider
                lastException = e;
            }
        }

        // If we get here, no provider had the resource
        throw new IOException("Resource not found in any provider: " + path, lastException);
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