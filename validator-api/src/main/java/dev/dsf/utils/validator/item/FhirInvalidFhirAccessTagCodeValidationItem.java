package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the FHIR access tag code is invalid.
 * Corresponds to {@link ValidationType#INVALID_FHIR_ACCESS_TAG_CODE}.
 */
public class FhirInvalidFhirAccessTagCodeValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item for an invalid FHIR access tag code using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirInvalidFhirAccessTagCodeValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR, resourceFile, fhirReference, ValidationType.INVALID_FHIR_ACCESS_TAG_CODE,
                "Invalid FHIR access tag code");
    }

    /**
     * Constructs a new validation item for an invalid FHIR access tag code using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom validation message describing the issue
     */
    public FhirInvalidFhirAccessTagCodeValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR, resourceFile, fhirReference, ValidationType.INVALID_FHIR_ACCESS_TAG_CODE, description);
    }
}
