package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains no
 * {@code requester.identifier.value} element.
 *
 * <p>According to the DSF {@code task-base} profile, the {@code requester.identifier.value}
 * must be set. Missing this element means the requester organization cannot be resolved.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_REQUESTER_ID_MISSING}.</p>
 */
public class FhirTaskRequesterIdNotExistValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating a missing {@code requester.identifier.value}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskRequesterIdNotExistValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_REQUESTER_ID_MISSING,
                description);
    }

    /**
     * Constructs a validation item using the default message for missing {@code requester.identifier.value}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskRequesterIdNotExistValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task.requester.identifier.value is missing.");
    }
}
