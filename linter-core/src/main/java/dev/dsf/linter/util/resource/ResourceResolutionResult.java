package dev.dsf.linter.util.resource;

import java.io.File;
import java.util.Optional;

/**
 * Represents the result of a resource resolution operation.
 * <p>
 * This record encapsulates information about where a resource was found (or not found),
 * including the file location, the source of the resolution (disk, classpath, dependency),
 * and metadata about expected vs. actual locations.
 * </p>
 * <p>
 * The resolution source indicates where the resource was located:
 * </p>
 * <ul>
 *   <li>{@link ResolutionSource#DISK_IN_ROOT} - Found on disk within the expected resource root</li>
 *   <li>{@link ResolutionSource#DISK_OUTSIDE_ROOT} - Found on disk but outside the expected resource root</li>
 *   <li>{@link ResolutionSource#CLASSPATH_DEPENDENCY} - Found in a dependency JAR</li>
 *   <li>{@link ResolutionSource#NOT_FOUND} - Resource was not found anywhere</li>
 * </ul>
 *
 * @param file the resolved file, empty if not found
 * @param source the source type of the resolution
 * @param expectedRoot the expected resource root directory path
 * @param actualLocation the actual location where the resource was found
 */
public record ResourceResolutionResult(
        Optional<File> file,
        ResolutionSource source,
        String expectedRoot,
        String actualLocation
) {

    /**
     * Source type for resource resolution.
     */
    public enum ResolutionSource {
        /** Found on disk within expected resource root */
        DISK_IN_ROOT,

        /** Found on disk but outside expected resource root */
        DISK_OUTSIDE_ROOT,

        /** Found via classpath and materialized (legacy fallback) */
        CLASSPATH_MATERIALIZED,

        /** Found in dependency JAR */
        CLASSPATH_DEPENDENCY,

        /** Not found anywhere */
        NOT_FOUND
    }

    /**
     * Creates a NOT_FOUND result.
     *
     * @param expectedRoot the expected resource root
     * @return result indicating resource was not found
     */
    public static ResourceResolutionResult notFound(String expectedRoot) {
        return new ResourceResolutionResult(
                Optional.empty(),
                ResolutionSource.NOT_FOUND,
                expectedRoot,
                null
        );
    }

    /**
     * Creates an IN_ROOT result.
     *
     * @param file the found file
     * @param expectedRoot the expected resource root
     * @return result indicating resource was found in expected location
     */
    public static ResourceResolutionResult inRoot(File file, File expectedRoot) {
        return new ResourceResolutionResult(
                Optional.of(file),
                ResolutionSource.DISK_IN_ROOT,
                expectedRoot.getAbsolutePath(),
                file.getAbsolutePath()
        );
    }

    /**
     * Creates an OUTSIDE_ROOT result.
     *
     * @param file the found file
     * @param expectedRoot the expected resource root
     * @return result indicating resource was found outside expected location
     */
    public static ResourceResolutionResult outsideRoot(File file, File expectedRoot) {
        return new ResourceResolutionResult(
                Optional.of(file),
                ResolutionSource.DISK_OUTSIDE_ROOT,
                expectedRoot.getAbsolutePath(),
                file.getAbsolutePath()
        );
    }

    /**
     * Creates a DEPENDENCY result.
     *
     * @param file the materialized file
     * @param dependencyJar the source dependency JAR
     * @param expectedRoot the expected resource root
     * @return result indicating resource was found in dependency
     */
    public static ResourceResolutionResult fromDependency(File file, String dependencyJar, File expectedRoot) {
        return new ResourceResolutionResult(
                Optional.of(file),
                ResolutionSource.CLASSPATH_DEPENDENCY,
                expectedRoot.getAbsolutePath(),
                "dependency:" + dependencyJar
        );
    }


}