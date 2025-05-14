package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that the required
 * <code>meta.tag</code> with system {@code http://dsf.dev/fhir/CodeSystem/read-access-tag}
 * and code {@code ALL} is missing in a ValueSet resource.
 */
public class FhirValueSetMissingReadAccessTagValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item indicating a missing read-access-tag
     * in the meta.tag of a ValueSet.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     * @param description    a human-readable description of the error
     */
    public FhirValueSetMissingReadAccessTagValidationItem(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_READ_ACCESS_TAG,
                description
        );
    }

    /**
     * Constructs a new validation error item indicating a missing read-access-tag
     * with an explicit FHIR resource ID.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     * @param description    a human-readable description of the error
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetMissingReadAccessTagValidationItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_READ_ACCESS_TAG,
                description,
                resourceId
        );
    }

    @Override
    public String toString()
    {
        return String.format(
                "[%s] %s (fhirReference=%s, file=%s, description=%s)",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFhirReference(),
                getResourceFile(),
                getDescription()
        );
    }
}
