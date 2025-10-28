package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that a ValueSet is missing the required <code>title</code> element.
 * <p>
 * The <code>title</code> provides a human-readable name for the ValueSet and should always be present
 * to aid documentation and referencing.
 * </p>
 */
public class FhirValueSetMissingTitleValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item for a ValueSet missing its <code>title</code> element.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     */
    public FhirValueSetMissingTitleValidationItem(
            File resourceFile,
            String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_TITLE,
                "ValueSet is missing required <title> element"
        );
    }

    /**
     * Constructs a new validation error item for a ValueSet missing its <code>title</code> element,
     * explicitly referencing the resource ID if available.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetMissingTitleValidationItem(
            File resourceFile,
            String fhirReference,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_TITLE,
                "ValueSet is missing required <title> element",
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
