package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint error indicating that the <code>status</code> element in a FHIR Task
 * resource does not have the required value <code>draft</code>.
 * <p>
 * In DSF Task resources under development, the <code>status</code> must always be set to <code>draft</code>
 * to indicate that the resource is not yet active or in production.
 * </p>
 */
public class FhirTaskStatusNotDraftLintItem extends FhirElementLintItem {
    /**
     * Constructs a new error indicating that the <code>status</code> element does not have the value <code>draft</code>.
     *
     * @param resourceFile  the FHIR Task file where the issue was detected
     * @param fhirReference a canonical or logical reference to the resource
     */
    public FhirTaskStatusNotDraftLintItem(File resourceFile, String fhirReference) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.TASK_STATUS_NOT_DRAFT,
                "The <status> element must be set to 'draft'."
        );
    }

    /**
     * Constructs a new error with a custom message for an invalid <code>status</code> value.
     *
     * @param resourceFile  the FHIR Task file where the issue was detected
     * @param fhirReference a canonical or logical reference to the resource
     * @param description   explanation of the issue
     */
    public FhirTaskStatusNotDraftLintItem(File resourceFile, String fhirReference, String description) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.TASK_STATUS_NOT_DRAFT,
                description
        );
    }
}
