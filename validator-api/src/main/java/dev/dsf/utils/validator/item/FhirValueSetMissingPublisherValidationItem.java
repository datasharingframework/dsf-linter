package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that a ValueSet is missing the required <code>publisher</code> element.
 * <p>
 * The <code>publisher</code> identifies the organization or individual responsible for the ValueSet.
 * This field is mandatory in DSF ValueSets.
 * </p>
 */
public class FhirValueSetMissingPublisherValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item for a ValueSet missing its <code>publisher</code> element.
     *
     * @param resourceFile   the FHIR resource file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     */
    public FhirValueSetMissingPublisherValidationItem(
            File resourceFile,
            String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_PUBLISHER,
                "ValueSet is missing required <publisher> element"
        );
    }

    /**
     * Constructs a new validation error item for a ValueSet missing its <code>publisher</code> element,
     * explicitly referencing the resource ID if available.
     *
     * @param resourceFile   the FHIR resource file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR ValueSet
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetMissingPublisherValidationItem(
            File resourceFile,
            String fhirReference,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_MISSING_PUBLISHER,
                "ValueSet is missing required <publisher> element",
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
