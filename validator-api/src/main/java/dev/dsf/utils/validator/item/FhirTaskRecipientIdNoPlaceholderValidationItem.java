package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains a
 * {@code restriction.recipient.identifier.value} without the required
 * {@code #{organization}} placeholder (development context).
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_RECIPIENT_ID_NO_PLACEHOLDER}.</p>
 */
public class FhirTaskRecipientIdNoPlaceholderValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item with a custom message.
     *
     * @param resourceFile   the file in which the FHIR Task resource was found
     * @param fhirReference  the canonical URL or local identifier of the resource
     * @param message        the message to describe the validation issue
     */
    public FhirTaskRecipientIdNoPlaceholderValidationItem(File resourceFile, String fhirReference, String message)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_RECIPIENT_ID_NO_PLACEHOLDER,
                message);
    }

    /**
     * Constructs a validation item using the default message for a missing
     * {@code #{organization}} placeholder.
     *
     * @param resourceFile   the file in which the FHIR Task resource was found
     * @param fhirReference  the canonical URL or local identifier of the resource
     */
    public FhirTaskRecipientIdNoPlaceholderValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task.restriction.recipient.identifier.value does not contain the '#{organization}' placeholder.");
    }
}
