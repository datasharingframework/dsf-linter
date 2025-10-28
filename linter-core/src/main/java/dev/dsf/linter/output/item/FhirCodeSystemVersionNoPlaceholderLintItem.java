package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation warning indicating that the <code>version</code> field of a FHIR CodeSystem
 * does not contain the required placeholder <code>#{version}</code>.
 * <p>
 * In DSF CodeSystems, the version must be left as a placeholder during development
 * to be automatically replaced by the build pipeline.
 * </p>
 */
public class FhirCodeSystemVersionNoPlaceholderLintItem extends FhirElementLintItem {
    /**
     * Constructs a new warning indicating that the <code>version</code> field is missing
     * the <code>#{version}</code> placeholder in the given CodeSystem.
     *
     * @param resourceFile  the FHIR CodeSystem file where the issue was detected
     * @param fhirReference a canonical or logical reference to the resource
     */
    public FhirCodeSystemVersionNoPlaceholderLintItem(File resourceFile, String fhirReference) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.CODE_SYSTEM_VERSION_NO_PLACEHOLDER,
                "The <version> element is missing the placeholder '#{version}'."
        );
    }

    /**
     * Constructs a new warning with a custom message for a missing <code>#{version}</code> placeholder.
     *
     * @param resourceFile  the FHIR CodeSystem file where the issue was detected
     * @param fhirReference a canonical or logical reference to the resource
     * @param description   explanation of the issue
     */
    public FhirCodeSystemVersionNoPlaceholderLintItem(File resourceFile, String fhirReference, String description) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.CODE_SYSTEM_VERSION_NO_PLACEHOLDER,
                description
        );
    }
}
