package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource is missing the required
 * {@code <status>} element or that it is empty.
 *
 * <p>According to the FHIR specification and the DSF {@code task-base} profile,
 * the {@code status} element is mandatory. It must be present and non-blank.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_MISSING_STATUS}.</p>
 */
public class FhirTaskMissingStatusValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating a missing or empty {@code <status>} element.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskMissingStatusValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_MISSING_STATUS,
                description);
    }

    /**
     * Constructs a validation item using the default error message for a missing {@code status}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskMissingStatusValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference, "Task is missing <status> or it is empty.");
    }
}
