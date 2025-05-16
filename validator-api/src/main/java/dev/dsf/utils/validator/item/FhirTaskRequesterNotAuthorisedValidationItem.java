package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * <p>
 * Validation item indicating that the requester organization specified in a
 * FHIR Task resource is not authorized according to the corresponding
 * ActivityDefinition.
 * </p>
 *
 * <p>
 * This validation is part of DSF security checks, ensuring that a Task's
 * {@code requester.identifier.value} matches one of the authorized organizations
 * declared in the {@code extension-process-authorization} of the ActivityDefinition.
 * </p>
 *
 * <p>
 * This issue is reported with {@link ValidationSeverity#ERROR} and uses the
 * validation type {@link ValidationType#FHIR_TASK_REQUESTER_NOT_AUTHORIZED}.
 * </p>
 *
 * <h3>Typical Use Case:</h3>
 * <ul>
 *   <li>Detecting when a Task references an ActivityDefinition, but the requester is not permitted to initiate the process.</li>
 *   <li>Preventing unauthorized or misconfigured Task execution in DSF deployments.</li>
 * </ul>
 *
 * <h3>Example Issue:</h3>
 * <pre>
 * {
 *   "severity": "ERROR",
 *   "file": "task-update-allow-list.xml",
 *   "reference": "http://dsf.dev/bpe/Process/updateAllowList",
 *   "type": "FHIR_TASK_REQUESTER_NOT_AUTHORIZED",
 *   "description": "Organisation 'foo.org' is not authorised according to ActivityDefinition dsf-update-allow-list.xml"
 * }
 * </pre>
 */
public class FhirTaskRequesterNotAuthorisedValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error indicating that the requester is not authorized.
     *
     * @param resourceFile  the FHIR Task resource file in which the issue was found
     * @param fhirReference the canonical reference from the Task (e.g., {@code instantiatesCanonical} value)
     * @param description   additional context or explanation of the issue
     */
    public FhirTaskRequesterNotAuthorisedValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_TASK_REQUESTER_NOT_AUTHORIZED,
                description
        );
    }
}
