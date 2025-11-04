package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint warning indicating that the {@code instantiatesCanonical} element in a FHIR Task
 * resource is missing expected placeholder values (e.g., {@code #{version}}).
 * <p>
 * During development, Task resources should contain placeholder values that will be replaced
 * during the build process. This warning helps identify cases where placeholders are missing.
 * </p>
 */
public class FhirTaskInstantiatesCanonicalPlaceholderLintItem extends FhirElementLintItem {
    /**
     * Constructs a new warning indicating that the {@code instantiatesCanonical} element is missing placeholders.
     *
     * @param resourceFile  the FHIR Task file where the issue was detected
     * @param fhirReference a canonical or logical reference to the resource
     */
    public FhirTaskInstantiatesCanonicalPlaceholderLintItem(File resourceFile, String fhirReference) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.TASK_INSTANTIATES_CANONICAL_PLACEHOLDER,
                "The <instantiatesCanonical> element is missing expected placeholder values (e.g., #{version})."
        );
    }

    /**
     * Constructs a new warning with a custom message for missing placeholder detection.
     *
     * @param resourceFile  the FHIR Task file where the issue was detected
     * @param fhirReference a canonical or logical reference to the resource
     * @param description   explanation of the issue
     */
    public FhirTaskInstantiatesCanonicalPlaceholderLintItem(File resourceFile, String fhirReference, String description) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.TASK_INSTANTIATES_CANONICAL_PLACEHOLDER,
                description
        );
    }
}
