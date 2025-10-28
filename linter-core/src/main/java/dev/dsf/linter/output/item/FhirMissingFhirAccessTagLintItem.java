package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that the FHIR access tag is missing.
 * Corresponds to {@link LintingType#MISSING_FHIR_ACCESS_TAG}.
 */
public class FhirMissingFhirAccessTagLintItem extends FhirElementLintItem {
    /**
     * Constructs a new validation item for a missing FHIR access tag using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirMissingFhirAccessTagLintItem(File resourceFile, String fhirReference) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.MISSING_FHIR_ACCESS_TAG,
                "Missing FHIR access tag");
    }

    /**
     * Constructs a new validation item for a missing FHIR access tag using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom validation message describing the issue
     */
    public FhirMissingFhirAccessTagLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.MISSING_FHIR_ACCESS_TAG,
                description);
    }
}
