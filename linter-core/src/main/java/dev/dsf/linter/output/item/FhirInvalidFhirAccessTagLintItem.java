package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that the FHIR access tag is invalid.
 * Corresponds to {@link LintingType#INVALID_FHIR_ACCESS_TAG}.
 */
public class FhirInvalidFhirAccessTagLintItem extends FhirElementLintItem {
    /**
     * Constructs a new validation item for an invalid FHIR access tag using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirInvalidFhirAccessTagLintItem(File resourceFile, String fhirReference) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.INVALID_FHIR_ACCESS_TAG,
                "Invalid FHIR access tag");
    }

    /**
     * Constructs a new validation item for an invalid FHIR access tag using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom validation message describing the issue
     */
    public FhirInvalidFhirAccessTagLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.INVALID_FHIR_ACCESS_TAG, description);
    }
}
