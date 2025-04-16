package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the FHIR tag system is invalid.
 * Corresponds to {@link ValidationType#INVALID_FHIR_TAG_SYSTEM}.
 */
public class FhirInvalidFhirTagSystemValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item for an invalid FHIR tag system using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirInvalidFhirTagSystemValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR, resourceFile, fhirReference, ValidationType.INVALID_FHIR_TAG_SYSTEM,
                "Invalid FHIR tag system");
    }

    /**
     * Constructs a new validation item for an invalid FHIR tag system using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom validation message describing the issue
     */
    public FhirInvalidFhirTagSystemValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR, resourceFile, fhirReference, ValidationType.INVALID_FHIR_TAG_SYSTEM, description);
    }
}
