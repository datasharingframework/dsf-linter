package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the ActivityDefinition entry is missing the 'requester' extension
 * in the process-authorization component.
 * Corresponds to {@link ValidationType#MISSING_REQUESTER_EXTENSION}.
 */
public class FhirActivityDefinitionEntryMissingRequesterValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item for a missing requester extension using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirActivityDefinitionEntryMissingRequesterValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.MISSING_REQUESTER_EXTENSION,
                "No <extension url='requester'> found in process-authorization.");
    }

    /**
     * Constructs a new validation item for a missing requester extension using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom validation message describing the issue
     */
    public FhirActivityDefinitionEntryMissingRequesterValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.MISSING_REQUESTER_EXTENSION,
                description);
    }
}

