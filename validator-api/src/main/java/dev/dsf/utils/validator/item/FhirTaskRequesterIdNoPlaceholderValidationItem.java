package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains a
 * {@code requester.identifier.value} without the {@code #{organization}} placeholder.
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_REQUESTER_ID_NO_PLACEHOLDER}.</p>
 */
public class FhirTaskRequesterIdNoPlaceholderValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating that the {@code requester.identifier.value}
     * is present but does not include the {@code #{organization}} placeholder.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskRequesterIdNoPlaceholderValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_REQUESTER_ID_NO_PLACEHOLDER,
                description);
    }

    /**
     * Constructs a validation item using the default message for missing {@code #{organization}} placeholder.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskRequesterIdNoPlaceholderValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task.requester.identifier.value does not contain the '#{organization}' placeholder.");
    }
}
