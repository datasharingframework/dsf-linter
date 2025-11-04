package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the ActivityDefinition entry is missing the 'recipient' extension
 * in the process-authorization component.
 * Corresponds to {@link LintingType#MISSING_RECIPIENT_EXTENSION}.
 */
public class FhirActivityDefinitionEntryMissingRecipientLintItem extends FhirElementLintItem {
    /**
     * Constructs a new Lint Item for a missing recipient extension using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirActivityDefinitionEntryMissingRecipientLintItem(File resourceFile, String fhirReference) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.MISSING_RECIPIENT_EXTENSION,
                "No <extension url='recipient'> found in process-authorization.");
    }

    /**
     * Constructs a new Lint Item for a missing recipient extension using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom lint description describing the issue
     */
    public FhirActivityDefinitionEntryMissingRecipientLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.MISSING_RECIPIENT_EXTENSION,
                description);
    }
}
