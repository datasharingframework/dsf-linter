package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR Task resource contains an unknown or invalid status value.
 *
 * <p>This validation item is reported when the Task.status value is not recognized as one of the
 * standard FHIR Task status codes. Valid Task status values include: draft, requested, received,
 * accepted, rejected, ready, cancelled, in-progress, on-hold, failed, completed, entered-in-error.</p>
 *
 * <p>This validation item corresponds to an ERROR severity level and helps ensure that Task
 * resources contain only valid status values as defined in the FHIR specification.</p>
 */
public class FhirTaskUnknownStatusValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating an unknown Task status.
     *
     * @param resourceFile  the file where the Task resource was found
     * @param fhirReference the canonical URL or local reference to the resource
     * @param description   a human-readable message describing the unknown status
     */
    public FhirTaskUnknownStatusValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_TASK_UNKNOWN_STATUS,
                description);
    }

}

