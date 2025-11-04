package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Represents a FHIR Lint warning indicating that a <code>code</code>
 * is duplicated within a single <code>compose.include</code> block.
 * <p>
 * While technically allowed in FHIR, duplicate codes in the same include block
 * can indicate configuration errors and should be avoided in DSF ValueSets.
 * </p>
 */
public class FhirValueSetDuplicateConceptCodeLintItem extends FhirElementLintItem {
    /**
     * Constructs a new lint warning for a duplicate <code>code</code> element in a ValueSet include block.
     *
     * @param resourceFile  the FHIR ValueSet file where the duplication was detected
     * @param fhirReference a canonical URL or local reference identifying the ValueSet
     * @param description   a human-readable description of the duplication
     */
    public FhirValueSetDuplicateConceptCodeLintItem(
            File resourceFile,
            String fhirReference,
            String description) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_VALUE_SET_DUPLICATE_CONCEPT_CODE,
                description
        );
    }

    /**
     * Constructs a new lint warning for a duplicate <code>code</code> element in a ValueSet include block,
     * including the resource ID if available.
     *
     * @param resourceFile  the FHIR ValueSet file where the duplication was detected
     * @param fhirReference a canonical URL or local reference identifying the ValueSet
     * @param description   a human-readable description of the duplication
     * @param resourceId    the ID of the ValueSet resource
     */
    public FhirValueSetDuplicateConceptCodeLintItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_VALUE_SET_DUPLICATE_CONCEPT_CODE,
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
