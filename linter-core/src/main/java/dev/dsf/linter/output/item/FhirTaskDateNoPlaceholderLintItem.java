package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint warning indicating that the <code>authoredOn</code> field in a FHIR Task
 * does not contain the required placeholder <code>#{date}</code>.
 * <p>
 * In DSF processes, the <code>authoredOn</code> field must include the placeholder
 * <code>#{date}</code> during development. This placeholder is later replaced automatically
 * by the BPE build system with a concrete timestamp.
 * </p>
 */
public class FhirTaskDateNoPlaceholderLintItem extends FhirElementLintItem {
    /**
     * Constructs a new warning indicating that the <code>authoredOn</code> date is missing the
     * <code>#{date}</code> placeholder in the given FHIR Task file.
     *
     * @param resourceFile  the FHIR Task file where the issue was detected
     * @param fhirReference a canonical or logical reference to the resource
     */
    public FhirTaskDateNoPlaceholderLintItem(File resourceFile, String fhirReference) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_TASK_DATE_NO_PLACEHOLDER,
                "The <authoredOn> date field is missing the placeholder '#{date}'."
        );
    }

    /**
     * Constructs a new warning with a custom description for a missing <code>#{date}</code> placeholder.
     *
     * @param resourceFile  the FHIR Task file where the issue was detected
     * @param fhirReference a canonical or logical reference to the resource
     * @param description   custom explanation of the issue
     */
    public FhirTaskDateNoPlaceholderLintItem(File resourceFile, String fhirReference, String description) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_TASK_DATE_NO_PLACEHOLDER,
                description
        );
    }
}
