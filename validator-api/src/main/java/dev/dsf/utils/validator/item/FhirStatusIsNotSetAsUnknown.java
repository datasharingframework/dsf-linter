package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the FHIR status is unknown.
 * Corresponds to {@link ValidationType#FHIR_STATUS_IS_NOT_SET_AS_UNKNOWN}.
 */
public class FhirStatusIsNotSetAsUnknown extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item with a default description.
     *
     * @param resourceFile the file where the resource was loaded from
     * @param fhirStatus   the FHIR status (e.g., a string representing the status)
     */
    public FhirStatusIsNotSetAsUnknown(File resourceFile, String fhirStatus)
    {
        super(ValidationSeverity.WARN, resourceFile, fhirStatus, ValidationType.FHIR_STATUS_IS_NOT_SET_AS_UNKNOWN,
                "FHIR status is not set as unknown");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param resourceFile the file where the resource was loaded from
     * @param fhirStatus   the FHIR status (e.g., a string representing the status)
     * @param description  a custom validation description
     */
    public FhirStatusIsNotSetAsUnknown(File resourceFile, String fhirStatus, String description)
    {
        super(ValidationSeverity.WARN, resourceFile, fhirStatus, ValidationType.FHIR_STATUS_IS_NOT_SET_AS_UNKNOWN, description);
    }
}
