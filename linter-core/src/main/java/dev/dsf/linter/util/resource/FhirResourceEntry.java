package dev.dsf.linter.util.resource;

import java.util.Objects;

/**
 * Value object describing a FHIR resource.
 * Simplified version that works with generic providers.
 */
public record FhirResourceEntry(String path, String fileName) {

    public FhirResourceEntry {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(fileName, "fileName cannot be null");
    }

    public static FhirResourceEntry fromPath(String path) {
        int idx = path.lastIndexOf('/');
        String file = (idx >= 0) ? path.substring(idx + 1) : path;
        return new FhirResourceEntry(path, file);
    }

    public ResourceType resourceType() {
        return ResourceType.FHIR;
    }
}