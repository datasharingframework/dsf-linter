package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation error indicating that a <code>&lt;concept&gt;</code> element
 * in a <code>&lt;compose&gt;&lt;include&gt;</code> block is missing the required <code>code</code> element.
 * <p>
 * In DSF ValueSets, every concept inside an include must specify a valid code to be meaningful.
 * </p>
 */
public class FhirValueSetConceptMissingCodeValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error item for a concept without a code.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a human-readable message describing the problem
     */
    public FhirValueSetConceptMissingCodeValidationItem(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_CONCEPT_MISSING_CODE,
                description
        );
    }

    /**
     * Constructs a new validation error item for a concept without a code,
     * including the resource ID if available.
     *
     * @param resourceFile   the FHIR ValueSet file where the issue was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a human-readable message describing the problem
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetConceptMissingCodeValidationItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_CONCEPT_MISSING_CODE,
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
