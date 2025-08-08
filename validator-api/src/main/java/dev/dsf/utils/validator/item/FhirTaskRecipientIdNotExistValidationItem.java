package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains no
 * {@code restriction.recipient.identifier.value} element.
 *
 * <p>According to the DSF {@code task-base} profile (development context),
 * the {@code restriction.recipient.identifier.value} should be set. Missing this element
 * means the recipient organization cannot be resolved.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_RECIPIENT_ID_MISSING}.</p>
 */
public class FhirTaskRecipientIdNotExistValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item with a custom message.
     *
     * @param resourceFile   the file in which the FHIR Task resource was found
     * @param fhirReference  the canonical URL or local identifier of the resource
     * @param message        the message to describe the validation issue
     */
    public FhirTaskRecipientIdNotExistValidationItem(File resourceFile, String fhirReference, String message)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_RECIPIENT_ID_MISSING,
                message);
    }

    /**
     * Constructs a validation item using the default message for a missing
     * {@code restriction.recipient.identifier.value}.
     *
     * @param resourceFile   the file in which the FHIR Task resource was found
     * @param fhirReference  the canonical URL or local identifier of the resource
     */
    public FhirTaskRecipientIdNotExistValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task.restriction.recipient.identifier.value is missing.");
    }
}
