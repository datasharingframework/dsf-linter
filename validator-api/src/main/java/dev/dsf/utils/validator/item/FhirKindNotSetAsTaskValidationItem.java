package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that the resource kind is not set as Task.
 * <p>
 * This item is used to flag FHIR resources that do not meet the requirement
 * to have their kind defined as Task.
 * </p>
 */
public class FhirKindNotSetAsTaskValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item for FHIR resources that are not defined as a Task.
     *
     * @param resourceFile   the FHIR resource file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR resource
     * @param description    a human-readable description of the error
     */
    public FhirKindNotSetAsTaskValidationItem(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(ValidationSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, ValidationType.FHIR_KIND_NOT_SET_AS_TASK, description);
    }

    /**
     * Constructs a new validation error item for FHIR resources that are not defined as a Task,
     * including a known resource ID.
     *
     * @param resourceFile   the FHIR resource file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the FHIR resource
     * @param description    a human-readable description of the error
     * @param resourceId     the FHIR resource ID, if known
     */
    public FhirKindNotSetAsTaskValidationItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(ValidationSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, ValidationType.FHIR_KIND_NOT_SET_AS_TASK, description, resourceId);
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
