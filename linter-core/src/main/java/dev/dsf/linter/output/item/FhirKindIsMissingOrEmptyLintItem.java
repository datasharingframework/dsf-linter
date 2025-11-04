package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the FHIR kind is invalid.
 * Corresponds to {@link LintingType#INVALID_FHIR_KIND}.
 */
public class FhirKindIsMissingOrEmptyLintItem extends FhirElementLintItem {
    /**
     * Constructs a new Lint Item with a default description.
     *
     * @param resourceFile the file where the resource was loaded from
     * @param fhirKind     the FHIR kind (e.g., a string representing the kind)
     */
    public FhirKindIsMissingOrEmptyLintItem(File resourceFile, String fhirKind) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirKind, LintingType.INVALID_FHIR_KIND,
                "FHIR kind is missing or empty");
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param resourceFile the file where the resource was loaded from
     * @param fhirKind     the FHIR kind (e.g., a string representing the kind)
     * @param description  a custom lint description
     */
    public FhirKindIsMissingOrEmptyLintItem(File resourceFile, String fhirKind, String description) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirKind, LintingType.INVALID_FHIR_KIND, description);
    }
}
