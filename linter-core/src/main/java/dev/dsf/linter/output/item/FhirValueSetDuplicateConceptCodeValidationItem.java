package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Represents a FHIR validation warning indicating that a <code>code</code>
 * is duplicated within a single <code>compose.include</code> block.
 * <p>
 * While technically allowed in FHIR, duplicate codes in the same include block
 * can indicate configuration errors and should be avoided in DSF ValueSets.
 * </p>
 */
public class FhirValueSetDuplicateConceptCodeValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation warning for a duplicate <code>code</code> element in a ValueSet include block.
     *
     * @param resourceFile   the FHIR ValueSet file where the duplication was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a human-readable description of the duplication
     */
    public FhirValueSetDuplicateConceptCodeValidationItem(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_DUPLICATE_CONCEPT_CODE,
                description
        );
    }

    /**
     * Constructs a new validation warning for a duplicate <code>code</code> element in a ValueSet include block,
     * including the resource ID if available.
     *
     * @param resourceFile   the FHIR ValueSet file where the duplication was detected
     * @param fhirReference  a canonical URL or local reference identifying the ValueSet
     * @param description    a human-readable description of the duplication
     * @param resourceId     the ID of the ValueSet resource
     */
    public FhirValueSetDuplicateConceptCodeValidationItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_DUPLICATE_CONCEPT_CODE,
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
