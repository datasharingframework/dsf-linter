package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the requester entry in an ActivityDefinition is invalid.
 * Corresponds to {@link ValidationType#ACTIVITY_DEFINITION_ENTRY_INVALID_REQUESTER}.
 */
public class FhirActivityDefinitionEntryInvalidRequesterValidationItem extends FhirElementValidationItem
{
    public FhirActivityDefinitionEntryInvalidRequesterValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, ValidationType.ACTIVITY_DEFINITION_ENTRY_INVALID_REQUESTER,
                "Invalid requester in ActivityDefinition entry");
    }

    public FhirActivityDefinitionEntryInvalidRequesterValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, ValidationType.ACTIVITY_DEFINITION_ENTRY_INVALID_REQUESTER, description);
    }
}
