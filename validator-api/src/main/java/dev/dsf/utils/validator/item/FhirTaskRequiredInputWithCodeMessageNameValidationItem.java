package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource is missing the required
 * {@code Task.input} slice with {@code type.coding.code = "message-name"}.
 *
 * <p>According to the DSF {@code task-base} profile, every Task must include exactly one input slice
 * of type {@code message-name} to identify the type of BPMN message the Task represents.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#TASK_INPUT_MISSING_MESSAGE_NAME}.</p>
 */
public class FhirTaskRequiredInputWithCodeMessageNameValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item using a custom message for a missing 'message-name' input slice.
     *
     * @param resourceFile  the file where the FHIR Task resource was loaded from
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a custom message describing the validation issue
     */
    public FhirTaskRequiredInputWithCodeMessageNameValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.TASK_INPUT_MISSING_MESSAGE_NAME,
                description);
    }

    /**
     * Constructs a validation item using the default error message for a missing 'message-name' input.
     *
     * @param resourceFile  the file where the FHIR Task resource was loaded from
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskRequiredInputWithCodeMessageNameValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task must contain exactly one input slice with code 'message-name'.");
    }
}
