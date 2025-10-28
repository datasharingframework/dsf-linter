package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Represents a FHIR validation warning indicating that the <code>version</code> field of a ValueSet
 * does not contain the required placeholder <code>#{version}</code>.
 * <p>
 * In DSF processes, the version is automatically managed by the BPE server and must contain
 * the placeholder <code>#{version}</code> in development artifacts.
 * </p>
 */
public class FhirValueSetVersionNoPlaceholderLintItem extends FhirElementLintItem {
    /**
     * Constructs a new validation warning for missing <code>#{version}</code> in the version field.
     *
     * @param resourceFile  the FHIR ValueSet file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the ValueSet
     * @param description   human-readable explanation of the placeholder requirement
     */
    public FhirValueSetVersionNoPlaceholderLintItem(
            File resourceFile,
            String fhirReference,
            String description) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_VALUE_SET_VERSION_NO_PLACEHOLDER,
                description
        );
    }

    /**
     * Constructs a new validation warning for missing <code>#{version}</code> with an explicit resource ID.
     *
     * @param resourceFile  the FHIR ValueSet file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the ValueSet
     * @param description   human-readable explanation of the placeholder requirement
     * @param resourceId    the ID of the ValueSet resource
     */
    public FhirValueSetVersionNoPlaceholderLintItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_VALUE_SET_VERSION_NO_PLACEHOLDER,
                description,
                resourceId
        );
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] %s (fhirReference=%s, file=%s, description=%s)",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFhirReference(),
                getResourceFile(),
                getDescription()
        );
    }
}
