package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a {@code Task.input} element with {@code type.coding.code = "business-key"}
 * is missing, even though the {@code Task.status} value requires it.
 *
 * <p>According to the DSF {@code task-base} profile, if {@code Task.status} is set to one of
 * {@code in-progress}, {@code completed}, or {@code failed}, then the {@code business-key}
 * input slice must be present.</p>
 *
 * <p>This Lint issue corresponds to {@link LintingType#TASK_BUSINESS_KEY_REQUIRED_FOR_STATUS}.</p>
 */
public class FhirTaskStatusRequiredInputBusinessKeyLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item using a custom message for a missing 'business-key' when required by {@code status}.
     *
     * @param resourceFile  the file where the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a custom message describing the Lint issue
     */
    public FhirTaskStatusRequiredInputBusinessKeyLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.TASK_BUSINESS_KEY_REQUIRED_FOR_STATUS,
                description);
    }

    /**
     * Constructs a Lint Item using the default message for missing 'business-key' required by {@code status}.
     *
     * @param resourceFile  the file where the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskStatusRequiredInputBusinessKeyLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task.status is one of 'in-progress', 'completed', or 'failed', so a 'business-key' input is required but missing.");
    }
}
