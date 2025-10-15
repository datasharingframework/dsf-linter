package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the ActivityDefinition entry is missing the 'recipient' extension
 * in the process-authorization component.
 * Corresponds to {@link ValidationType#MISSING_RECIPIENT_EXTENSION}.
 */
public class FhirActivityDefinitionEntryMissingRecipientValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item for a missing recipient extension using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirActivityDefinitionEntryMissingRecipientValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.MISSING_RECIPIENT_EXTENSION,
                "No <extension url='recipient'> found in process-authorization.");
    }

    /**
     * Constructs a new validation item for a missing recipient extension using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom validation message describing the issue
     */
    public FhirActivityDefinitionEntryMissingRecipientValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.MISSING_RECIPIENT_EXTENSION,
                description);
    }
}
