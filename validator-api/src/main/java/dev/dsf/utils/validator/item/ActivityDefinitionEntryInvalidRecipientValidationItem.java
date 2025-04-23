package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the recipient entry in an ActivityDefinition is invalid.
 * Corresponds to {@link ValidationType#ACTIVITY_DEFINITION_ENTRY_INVALID_RECIPIENT}.
 */
public class ActivityDefinitionEntryInvalidRecipientValidationItem extends FhirElementValidationItem
{
    public ActivityDefinitionEntryInvalidRecipientValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR, resourceFile, fhirReference, ValidationType.ACTIVITY_DEFINITION_ENTRY_INVALID_RECIPIENT,
                "Invalid recipient in ActivityDefinition entry");
    }

    public ActivityDefinitionEntryInvalidRecipientValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR, resourceFile, fhirReference, ValidationType.ACTIVITY_DEFINITION_ENTRY_INVALID_RECIPIENT, description);
    }
}
