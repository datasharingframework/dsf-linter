package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the FHIR status is invalid.
 * Corresponds to {@link ValidationType#FHIR_STATUS_IS_NOT_SET_AS_UNKNOWN}.
 */
public class InvalidFhirStatusValidationItem extends FhirElementValidationItem
{
    public InvalidFhirStatusValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.WARN, resourceFile, fhirReference, ValidationType.FHIR_STATUS_IS_NOT_SET_AS_UNKNOWN,
                "FHIR status is invalid");
    }

    public InvalidFhirStatusValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.WARN, resourceFile, fhirReference, ValidationType.FHIR_STATUS_IS_NOT_SET_AS_UNKNOWN, description);
    }
}
