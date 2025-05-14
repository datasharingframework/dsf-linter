package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that a <code>&lt;compose&gt;&lt;include&gt;</code> element
 * is missing the required <code>system</code> attribute or element.
 * <p>
 * In DSF ValueSets, every <code>include</code> must specify a <code>system</code> URI to indicate
 * the CodeSystem being referenced.
 * </p>
 */
public class FhirValueSetIncludeMissingSystemValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item for a missing <code>system</code> in a ValueSet include section.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a message describing the problem
     */
    public FhirValueSetIncludeMissingSystemValidationItem(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_INCLUDE_MISSING_SYSTEM,
                description
        );
    }

    /**
     * Constructs a new validation error item for a missing <code>system</code> in a ValueSet include section,
     * including the resource ID if known.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a message describing the problem
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetIncludeMissingSystemValidationItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_INCLUDE_MISSING_SYSTEM,
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
