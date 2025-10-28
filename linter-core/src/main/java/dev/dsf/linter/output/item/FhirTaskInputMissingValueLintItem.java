package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a {@code Task.input} element is missing the required
 * {@code value[x]} element.
 *
 * <p>According to the FHIR specification and the DSF {@code task-base} profile,
 * each {@code Task.input} must include a {@code value[x]} element representing the actual input value.
 * This value is required for the input to be meaningful and usable.</p>
 *
 * <p>This validation issue corresponds to {@link LintingType#TASK_INPUT_MISSING_VALUE}.</p>
 */
public class FhirTaskInputMissingValueLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item with a custom message indicating that {@code Task.input} is missing {@code value[x]}.
     *
     * @param resourceFile  the file where the Task resource was found
     * @param fhirReference the canonical URL or local reference to the resource
     * @param description   a human-readable message describing the issue
     */
    public FhirTaskInputMissingValueLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.TASK_INPUT_MISSING_VALUE,
                description);
    }

    /**
     * Constructs a validation item using the default error message for a missing {@code value[x]}.
     *
     * @param resourceFile  the file where the Task resource was found
     * @param fhirReference the canonical URL or local reference to the resource
     */
    public FhirTaskInputMissingValueLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task.input is missing a value[x] element.");
    }
}
