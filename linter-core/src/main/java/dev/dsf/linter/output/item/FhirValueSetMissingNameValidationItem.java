package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that a ValueSet is missing the required <code>name</code> element.
 * <p>
 * The <code>name</code> element is important for identifying and referencing the ValueSet by a human-readable identifier.
 * This validation item is used when the element is missing or not set.
 * </p>
 */
public class FhirValueSetMissingNameValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item for a ValueSet missing its <code>name</code> element.
     *
     * @param resourceFile   the FHIR resource file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     */
    public FhirValueSetMissingNameValidationItem(
            File resourceFile,
            String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_NAME,
                "ValueSet is missing required <name> element"
        );
    }

    /**
     * Constructs a new validation error item for a ValueSet missing its <code>name</code> element,
     * explicitly referencing the resource ID if available.
     *
     * @param resourceFile   the FHIR resource file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetMissingNameValidationItem(
            File resourceFile,
            String fhirReference,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_NAME,
                "ValueSet is missing required <name> element",
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
