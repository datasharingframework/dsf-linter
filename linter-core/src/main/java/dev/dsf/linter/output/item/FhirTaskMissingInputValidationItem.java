package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource is missing the required
 * {@code <input>} element or that it is completely empty.
 *
 * <p>According to the DSF {@code task-base} profile, a Task must contain at least one input.
 * Inputs are used to pass necessary data for task execution.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_MISSING_INPUT}.</p>
 */
public class FhirTaskMissingInputValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating a missing or empty {@code <input>} element.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskMissingInputValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_MISSING_INPUT,
                description);
    }

    /**
     * Constructs a validation item using the default error message for a missing {@code input}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskMissingInputValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference, "Task is missing <input> or it is empty.");
    }
}
