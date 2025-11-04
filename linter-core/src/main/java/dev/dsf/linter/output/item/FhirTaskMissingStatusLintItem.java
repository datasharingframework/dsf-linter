package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a FHIR {@code Task} resource is missing the required
 * {@code <status>} element or that it is empty.
 *
 * <p>According to the FHIR specification and the DSF {@code task-base} profile,
 * the {@code status} element is mandatory. It must be present and non-blank.</p>
 *
 * <p>This Lint issue corresponds to {@link LintingType#Fhir_TASK_MISSING_STATUS}.</p>
 */
public class FhirTaskMissingStatusLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item indicating a missing or empty {@code <status>} element.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskMissingStatusLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_MISSING_STATUS,
                description);
    }

    /**
     * Constructs a Lint Item using the default error message for a missing {@code status}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskMissingStatusLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference, "Task is missing <status> or it is empty.");
    }
}
