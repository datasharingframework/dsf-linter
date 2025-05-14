package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource is missing an expected
 * {@code output} element of type {@code error}.
 *
 * <p>According to DSF process profiles, a Task in a failed or rejected state
 * must provide an {@code output} with a coding type of {@code error}
 * describing the reason for failure.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_MISSING_ERROR_OUTPUT}.</p>
 */
public class FhirTaskMissingErrorOutputValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating a missing {@code output} element of type {@code error}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskMissingErrorOutputValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_MISSING_ERROR_OUTPUT,
                description);
    }

    /**
     * Constructs a validation item using the default message for a missing {@code error} output.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskMissingErrorOutputValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task in error state is missing required output of type 'error'.");
    }
}
