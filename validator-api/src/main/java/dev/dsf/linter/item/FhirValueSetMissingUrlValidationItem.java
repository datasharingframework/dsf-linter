package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that a ValueSet is missing the required <code>url</code> element.
 * <p>
 * The <code>url</code> field is essential in FHIR resources as it defines the canonical identifier of the ValueSet.
 * This validation item is used when the element is missing or empty.
 * </p>
 */
public class FhirValueSetMissingUrlValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item for a ValueSet missing its <code>url</code> element.
     *
     * @param resourceFile   the FHIR resource file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     */
    public FhirValueSetMissingUrlValidationItem(
            File resourceFile,
            String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_URL,
                "ValueSet is missing required <url> element"
        );
    }

    /**
     * Constructs a new validation error item for a ValueSet missing its <code>url</code> element,
     * explicitly referencing the resource ID if available.
     *
     * @param resourceFile   the FHIR resource file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetMissingUrlValidationItem(
            File resourceFile,
            String fhirReference,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_URL,
                "ValueSet is missing required <url> element",
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
