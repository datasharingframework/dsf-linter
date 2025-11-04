package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a FHIR {@code Task} resource is missing the required
 * {@code <input>} element or that it is completely empty.
 *
 * <p>According to the DSF {@code task-base} profile, a Task must contain at least one input.
 * Inputs are used to pass necessary data for task execution.</p>
 *
 * <p>This Lint issue corresponds to {@link LintingType#Fhir_TASK_MISSING_INPUT}.</p>
 */
public class FhirTaskMissingInputLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item indicating a missing or empty {@code <input>} element.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskMissingInputLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_MISSING_INPUT,
                description);
    }

    /**
     * Constructs a Lint Item using the default error message for a missing {@code input}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskMissingInputLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference, "Task is missing <input> or it is empty.");
    }
}
