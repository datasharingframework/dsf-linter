package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code <requester.identifier.system>} of a FHIR {@code Task}
 * resource is invalid.
 *
 * <p>According to the DSF {@code task-base} profile, the {@code requester.identifier.system}
 * must be set to the fixed URI {@code http://dsf.dev/sid/organization-identifier}.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#INVALID_TASK_REQUESTER_SYSTEM}.</p>
 */
public class FhirTaskInvalidRequesterValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item for an invalid {@code requester.identifier.system} with a custom description.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskInvalidRequesterValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.INVALID_TASK_REQUESTER_SYSTEM,
                description);
    }

    /**
     * Constructs a validation item using the default error message for an invalid requester system.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskInvalidRequesterValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task.requester.identifier.system must be 'http://dsf.dev/sid/organization-identifier'.");
    }
}
