package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the FHIR status is invalid.
 * Corresponds to {@link ValidationType#INVALID_FHIR_STATUS}.
 */
public class InvalidFhirStatusValidationItem extends FhirElementValidationItem
{
    public InvalidFhirStatusValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR, resourceFile, fhirReference, ValidationType.INVALID_FHIR_STATUS,
                "FHIR status is invalid");
    }

    public InvalidFhirStatusValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR, resourceFile, fhirReference, ValidationType.INVALID_FHIR_STATUS, description);
    }
}
