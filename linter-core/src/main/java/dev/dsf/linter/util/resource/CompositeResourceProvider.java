package dev.dsf.linter.util.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

/**
 * A composite implementation of {@link ResourceProvider} that delegates to multiple providers.
 * <p>
 * This provider allows combining multiple resource sources (e.g., file system and JAR files)
 * into a single unified interface. When listing resources, it merges results from all
 * providers. When opening or checking resources, it searches providers in order until
 * a match is found.
 * </p>
 * <p>
 * Resource entries are deduplicated when listing to avoid duplicate entries from
 * multiple providers. The first provider that contains a resource is used when
 * opening resources.
 * </p>
 *
 * @param <T> the type of resource entries produced by this provider
 * @param providers list of underlying resource providers (immutable)
 * @param resourceTypeName human-readable name describing the resource type
 * @see ResourceProvider
 * @see FileSystemResourceProvider
 */
public record CompositeResourceProvider<T>(List<ResourceProvider<T>> providers,
                                           String resourceTypeName) implements ResourceProvider<T> {

    /**
     * Creates a composite resource provider from a list of providers.
     *
     * @param providers the list of resource providers to combine
     * @param resourceTypeName human-readable name for the resource type
     * @throws NullPointerException if providers or resourceTypeName is null
     * @throws IllegalArgumentException if the providers list is empty
     */
    public CompositeResourceProvider(List<ResourceProvider<T>> providers, String resourceTypeName) {
        Objects.requireNonNull(providers, "providers cannot be null");
        Objects.requireNonNull(resourceTypeName, "resourceTypeName cannot be null");

        if (providers.isEmpty()) {
            throw new IllegalArgumentException("At least one provider must be specified");
        }

        this.providers = List.copyOf(providers);
        this.resourceTypeName = resourceTypeName;
    }

    /**
     * Creates a composite resource provider from varargs providers.
     *
     * @param resourceTypeName human-readable name for the resource type
     * @param providers variable number of resource providers to combine
     */
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