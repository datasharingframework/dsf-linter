package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that the FHIR URL is invalid.
 * Corresponds to {@link LintingType#INVALID_FHIR_URL}.
 */
public class FhirActivityDefinitionInvalidFhirUrlLintItem extends FhirElementLintItem {
    /**
     * Constructs a new validation item with a default description.
     *
     * @param resourceFile  the file where the resource was loaded from
     * @param fhirReference the FHIR reference (e.g., URL)
     */
    public FhirActivityDefinitionInvalidFhirUrlLintItem(File resourceFile, String fhirReference) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.INVALID_FHIR_URL,
                "FHIR URL is invalid");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param resourceFile  the file where the resource was loaded from
     * @param fhirReference the FHIR reference (e.g., URL)
     * @param description   a custom validation description
     */
    public FhirActivityDefinitionInvalidFhirUrlLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.INVALID_FHIR_URL, description);
    }
}
