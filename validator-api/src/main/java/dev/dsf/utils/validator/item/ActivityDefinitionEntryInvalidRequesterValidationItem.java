package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the requester entry in an ActivityDefinition is invalid.
 * Corresponds to {@link ValidationType#ACTIVITY_DEFINITION_ENTRY_INVALID_REQUESTER}.
 */
public class ActivityDefinitionEntryInvalidRequesterValidationItem extends FhirElementValidationItem
{
    public ActivityDefinitionEntryInvalidRequesterValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR, resourceFile, fhirReference, ValidationType.ACTIVITY_DEFINITION_ENTRY_INVALID_REQUESTER,
                "Invalid requester in ActivityDefinition entry");
    }

    public ActivityDefinitionEntryInvalidRequesterValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR, resourceFile, fhirReference, ValidationType.ACTIVITY_DEFINITION_ENTRY_INVALID_REQUESTER, description);
    }
}
