package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the FHIR status is invalid.
 * Corresponds to {@link LintingType#INVALID_FHIR_STATUS}.
 */
public class FhirActivityDefinitionInvalidFhirStatusLintItem extends FhirElementLintItem {
    public FhirActivityDefinitionInvalidFhirStatusLintItem(File resourceFile, String fhirReference) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.INVALID_FHIR_STATUS,
                "FHIR status is invalid");
    }

    public FhirActivityDefinitionInvalidFhirStatusLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.INVALID_FHIR_STATUS, description);
    }
}
