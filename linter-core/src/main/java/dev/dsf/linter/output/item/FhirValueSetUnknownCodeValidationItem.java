package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that a <code>code</code> inside a
 * <code>compose.include.concept</code> is not recognized as valid by DSF.
 * <p>
 * This check uses {@code FhirAuthorizationCache.isKnownDsfCode(system, code)} to determine
 * whether the specified combination is approved for use in DSF ValueSets.
 * </p>
 */
public class FhirValueSetUnknownCodeValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item for an unknown DSF code.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a message describing the unknown code
     */
    public FhirValueSetUnknownCodeValidationItem(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_UNKNOWN_CODE,
                description
        );
    }

    /**
     * Constructs a new validation error item for an unknown DSF code,
     * including a resource ID if available.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a message describing the unknown code
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetUnknownCodeValidationItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_UNKNOWN_CODE,
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
