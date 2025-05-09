package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource is missing the required
 * {@code <id>} element or that it is empty.
 *
 * <p>According to the FHIR specification and the DSF {@code task-base} profile,
 * the {@code id} element is mandatory. It must be present and non-empty.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_MISSING_ID}.</p>
 */
public class FhirTaskMissingIdValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating a missing or empty {@code <id>} element.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskMissingIdValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_MISSING_ID,
                description);
    }

    /**
     * Constructs a validation item using the default error message for a missing {@code id}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskMissingIdValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference, "Task is missing <id> or it is empty.");
    }
}
