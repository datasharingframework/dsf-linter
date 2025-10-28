package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation warning indicating that the <code>date</code> field
 * of a ValueSet does not contain the required placeholder <code>#{date}</code>.
 * <p>
 * In DSF processes, the date is automatically managed by the BPE server and must contain
 * the placeholder <code>#{date}</code> in development artifacts.
 * </p>
 */
public class FhirValueSetDateNoPlaceholderValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation warning for missing <code>#{date}</code> in the date field.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a human-readable description of the issue
     */
    public FhirValueSetDateNoPlaceholderValidationItem(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_DATE_NO_PLACEHOLDER,
                description
        );
    }

    /**
     * Constructs a new validation warning for missing <code>#{date}</code> in the date field,
     * including the resource ID if available.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a human-readable description of the issue
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetDateNoPlaceholderValidationItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_DATE_NO_PLACEHOLDER,
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
