package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the business key check was skipped for a FHIR Task
 * because the status does not require specific business key validation rules.
 *
 * <p>This informational item is reported when the Task status is neither in the set of
 * statuses that require a business key nor is it 'draft' (which prohibits business key).
 * In such cases, the business key validation is skipped as it's not applicable.</p>
 *
 * <p>This Lint Item corresponds to an INFO severity level and helps provide
 * transparency about which validation checks are being performed or skipped.</p>
 */
public class FhirTaskBusinessKeyCheckIsSkippedLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item indicating that business key validation was skipped.
     *
     * @param resourceFile  the file where the Task resource was found
     * @param fhirReference the canonical URL or local reference to the resource
     * @param description   a human-readable message describing why the check was skipped
     */
    public FhirTaskBusinessKeyCheckIsSkippedLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_TASK_BUSINESS_KEY_CHECK_SKIPPED,
                description);
    }

    /**
     * Constructs a Lint Item using the default message for skipped business key check.
     *
     * @param resourceFile  the file where the Task resource was found
     * @param fhirReference the canonical URL or local reference to the resource
     */
    public FhirTaskBusinessKeyCheckIsSkippedLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Business key validation check was skipped for this Task status.");
    }
}

