package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code Task.output} element is missing the required {@code value[x]} element.
 *
 * <p>According to the FHIR specification and the DSF {@code task-base} profile,
 * each {@code Task.output} must include a {@code value[x]} element to carry the output value
 * of the corresponding output type.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#TASK_OUTPUT_MISSING_VALUE}.</p>
 */
public class FhirTaskOutputMissingValueValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item using a custom message for a missing {@code value[x]} in {@code Task.output}.
     *
     * @param resourceFile  the file where the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable message describing the issue
     */
    public FhirTaskOutputMissingValueValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.TASK_OUTPUT_MISSING_VALUE,
                description);
    }

    /**
     * Constructs a validation item using the default message for a missing {@code value[x]} in {@code Task.output}.
     *
     * @param resourceFile  the file where the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskOutputMissingValueValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task.output is missing a value[x] element.");
    }
}
