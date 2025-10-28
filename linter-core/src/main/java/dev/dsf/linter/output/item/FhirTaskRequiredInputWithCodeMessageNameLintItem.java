package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource is missing the required
 * {@code Task.input} slice with {@code type.coding.code = "message-name"}.
 *
 * <p>According to the DSF {@code task-base} profile, every Task must include exactly one input slice
 * of type {@code message-name} to identify the type of BPMN message the Task represents.</p>
 *
 * <p>This validation issue corresponds to {@link LintingType#TASK_INPUT_MISSING_MESSAGE_NAME}.</p>
 */
public class FhirTaskRequiredInputWithCodeMessageNameLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item using a custom message for a missing 'message-name' input slice.
     *
     * @param resourceFile  the file where the FHIR Task resource was loaded from
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a custom message describing the validation issue
     */
    public FhirTaskRequiredInputWithCodeMessageNameLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.TASK_INPUT_MISSING_MESSAGE_NAME,
                description);
    }

    /**
     * Constructs a validation item using the default error message for a missing 'message-name' input.
     *
     * @param resourceFile  the file where the FHIR Task resource was loaded from
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskRequiredInputWithCodeMessageNameLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task must contain exactly one input slice with code 'message-name'.");
    }
}
