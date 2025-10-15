package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that a ValueSet is missing the required <code>description</code> element.
 * <p>
 * The <code>description</code> provides context and purpose of the ValueSet and is required in DSF-conformant resources.
 * </p>
 */
public class FhirValueSetMissingDescriptionValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item for a ValueSet missing its <code>description</code> element.
     *
     * @param resourceFile   the FHIR resource file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     */
    public FhirValueSetMissingDescriptionValidationItem(
            File resourceFile,
            String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_DESCRIPTION,
                "ValueSet is missing required <description> element"
        );
    }

    /**
     * Constructs a new validation error item for a ValueSet missing its <code>description</code> element,
     * explicitly referencing the resource ID if available.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetMissingDescriptionValidationItem(
            File resourceFile,
            String fhirReference,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_DESCRIPTION,
                "ValueSet is missing required <description> element",
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
