package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;
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
