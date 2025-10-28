package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation warning indicating that a <code>&lt;compose&gt;&lt;include&gt;</code>
 * element is missing the required <code>#{version}</code> placeholder in its <code>version</code> field.
 * <p>
 * In DSF, include version entries must contain the placeholder <code>#{version}</code>
 * so that the BPE server can dynamically replace it during deployment.
 * </p>
 */
public class FhirValueSetIncludeVersionPlaceholderValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation warning item for a ValueSet include.version element missing <code>#{version}</code>.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a human-readable message describing the problem
     */
    public FhirValueSetIncludeVersionPlaceholderValidationItem(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_INCLUDE_VERSION_NO_PLACEHOLDER,
                description
        );
    }

    /**
     * Constructs a new validation warning item for a ValueSet include.version element missing <code>#{version}</code>,
     * including a known resource ID.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a human-readable message describing the problem
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetIncludeVersionPlaceholderValidationItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_INCLUDE_VERSION_NO_PLACEHOLDER,
                description,
                resourceId
        );
    }

    @Override
    public String toString()
    {
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
