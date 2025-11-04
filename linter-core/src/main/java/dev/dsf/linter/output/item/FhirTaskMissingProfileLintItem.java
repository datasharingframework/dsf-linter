package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a FHIR {@code Task} resource is missing the required
 * {@code <meta.profile>} element or that it is empty.
 *
 * <p>According to the FHIR specification and the DSF {@code task-base} profile,
 * the {@code meta.profile} element is mandatory and must contain the profile URL.</p>
 *
 * <p>This Lint issue corresponds to {@link LintingType#Fhir_TASK_MISSING_PROFILE}.</p>
 */
public class FhirTaskMissingProfileLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item indicating a missing or empty {@code <meta.profile>} element.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskMissingProfileLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_MISSING_PROFILE,
                description);
    }

    /**
     * Constructs a Lint Item using the default error message for a missing {@code meta.profile}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskMissingProfileLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference, "Task is missing <meta.profile> or it is empty.");
    }
}
