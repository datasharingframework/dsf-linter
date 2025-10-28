package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that the recipient entry in an ActivityDefinition is invalid.
 * Corresponds to {@link LintingType#ACTIVITY_DEFINITION_ENTRY_INVALID_RECIPIENT}.
 */
public class FhirActivityDefinitionEntryInvalidRecipientLintItem extends FhirElementLintItem {
    public FhirActivityDefinitionEntryInvalidRecipientLintItem(File resourceFile, String fhirReference) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.ACTIVITY_DEFINITION_ENTRY_INVALID_RECIPIENT,
                "Invalid recipient in ActivityDefinition entry");
    }

    public FhirActivityDefinitionEntryInvalidRecipientLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.ACTIVITY_DEFINITION_ENTRY_INVALID_RECIPIENT, description);
    }
}
