package dev.dsf.linter.util.resource;

/**
 * Factory interface for creating resource entries from path strings.
 * Enables generic resource providers to work with different entry types.
 *
 * @param <T> the type of resource entry to create
 */
@FunctionalInterface
public interface ResourceEntryFactory<T> {

    /**
     * Creates a resource entry from a path string.
     *
     * @param path the resource path
     * @return the created resource entry
     */
    T create(String path);
}