package dev.dsf.linter.util.resource;

import java.util.Objects;

/**
 * Represents a FHIR resource file entry with its path and filename.
 * <p>
 * This record encapsulates the metadata for a FHIR resource file, providing
 * both the full relative path and the extracted filename. It serves as a
 * lightweight container for resource identification in the resource provider
 * abstraction.
 * </p>
 *
 * @param path the relative path to the resource file
 * @param fileName the filename extracted from the path
 * @see ResourceProvider
 * @see FileSystemResourceProvider
 */
public record FhirResourceEntry(String path, String fileName) {

    public FhirResourceEntry {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(fileName, "fileName cannot be null");
    }

    /**
     * Creates a FhirResourceEntry from a resource path.
     * <p>
     * Extracts the filename from the path by taking the substring after the last
     * forward slash. If no slash is present, the entire path is used as the filename.
     * </p>
     *
     * @param path the resource path
     * @return a new FhirResourceEntry instance
     */
    public static FhirResourceEntry fromPath(String path) {
        int idx = path.lastIndexOf('/');
        String file = (idx >= 0) ? path.substring(idx + 1) : path;
        return new FhirResourceEntry(path, file);
    }

    /**
     * Returns the resource type for this entry.
     *
     * @return always returns {@link ResourceType#FHIR}
     */
    public ResourceType resourceType() {
        return ResourceType.FHIR;
    }
}