package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * <p>
 * Validation item that represents an issue where the required
 * {@code requester} element is missing from a FHIR Task resource.
 * </p>
 *
 * <p>
 * Specifically, this validation checks whether
 * {@code requester.identifier.system} exists and is correctly specified.
 * In DSF Task resources, the requester is expected to identify the initiating
 * organization using the DSF organization identifier system.
 * </p>
 *
 * <p>
 * This item corresponds to the {@link ValidationType#FHIR_TASK_MISSING_REQUESTER}
 * type and is reported with {@link ValidationSeverity#ERROR} severity.
 * </p>
 *
 * <p>
 * Ensuring a valid requester is present is critical for process authorization,
 * task traceability, and cross-organization orchestration within DSF processes.
 * </p>
 *
 * <h3>Typical Use Case:</h3>
 * <ul>
 *   <li>Validating that the Task contains a {@code requester} element.</li>
 *   <li>Ensuring the requester system value points to the expected DSF identifier system.</li>
 * </ul>
 *
 * <h3>Example Issue:</h3>
 * <pre>
 * {
 *   "severity": "ERROR",
 *   "file": "task-example.xml",
 *   "reference": "Task/example",
 *   "type": "FHIR_TASK_MISSING_REQUESTER",
 *   "description": "Task.requester element is missing."
 * }
 * </pre>
 */
public class FhirTaskMissingRequesterValidationItem extends FhirElementValidationItem
{
    /**
     * Creates a new {@code FhirTaskMissingRequesterValidationItem} with a default message.
     *
     * @param resourceFile  the FHIR resource file where the issue was detected (may be {@code null})
     * @param fhirReference a canonical or local reference to the FHIR resource (e.g. {@code Task/example})
     */
    public FhirTaskMissingRequesterValidationItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_TASK_MISSING_REQUESTER,
                "Task.requester element is missing."
        );
    }

    /**
     * Creates a new {@code FhirTaskMissingRequesterValidationItem} with a custom description.
     *
     * @param resourceFile  the FHIR resource file where the issue was detected (may be {@code null})
     * @param fhirReference a canonical or local reference to the FHIR resource (e.g. {@code Task/example})
     * @param description   a custom human-readable explanation of the validation issue
     */
    public FhirTaskMissingRequesterValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_TASK_MISSING_REQUESTER,
                description
        );
    }
}
