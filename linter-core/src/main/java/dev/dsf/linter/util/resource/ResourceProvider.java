package dev.dsf.linter.util.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

/**
 * Generic interface for accessing resources from various sources.
 * <p>
 * This interface provides a unified API for discovering and reading resources
 * from different sources such as file systems, JAR files, or composite sources.
 * Implementations can abstract the underlying storage mechanism while providing
 * consistent resource access.
 * </p>
 * <p>
 * The type parameter {@code T} represents the type of resource entries returned
 * when listing resources, allowing implementations to provide metadata-rich
 * resource representations.
 * </p>
 *
 * @param <T> the type of resource entries produced by this provider
 * @see FileSystemResourceProvider
 * @see CompositeResourceProvider
 */
public interface ResourceProvider<T> {

    /**
     * Lists all resource entries in a specific directory.
     *
     * @param directory the relative directory path to search
     * @return a stream of resource entries found in the directory
     */
    Stream<T> listResources(String directory);

    /**
     * Opens a specific resource for reading.
     *
     * @param path the relative path to the resource
     * @return an InputStream for reading the resource content
     * @throws IOException if the resource cannot be opened or does not exist
     */
    InputStream openResource(String path) throws IOException;

    /**
     * Checks if a resource exists at the given path.
     *
     * @param path the relative path to check
     * @return true if the resource exists and is accessible, false otherwise
     */
    boolean resourceExists(String path);

    /**
     * Returns a human-readable description of this provider.
     *
     * @return a description of the resource provider
     */
    String getDescription();
}