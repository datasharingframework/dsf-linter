package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code <restriction.recipient.identifier.system>} element
 * of a FHIR {@code Task} resource is invalid.
 *
 * <p>According to the DSF {@code task-base} profile, the recipient's identifier system must be
 * {@code http://dsf.dev/sid/organization-identifier} to correctly reference the organization.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#INVALID_TASK_RECIPIENT_SYSTEM}.</p>
 */
public class FhirTaskInvalidRecipientValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item for an invalid recipient identifier system with a custom message.
     *
     * @param resourceFile  the file where the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable validation message
     */
    public FhirTaskInvalidRecipientValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.INVALID_TASK_RECIPIENT_SYSTEM,
                description);
    }

    /**
     * Constructs a validation item using the default message for an invalid recipient identifier system.
     *
     * @param resourceFile  the file where the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskInvalidRecipientValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task.restriction.recipient.identifier.system must be 'http://dsf.dev/sid/organization-identifier'.");
    }
}
