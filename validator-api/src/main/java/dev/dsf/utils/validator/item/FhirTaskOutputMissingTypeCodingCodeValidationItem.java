package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code Task.output} element is missing the required
 * {@code type.coding.code} sub-element.
 *
 * <p>According to the FHIR specification and the DSF {@code task-base} profile,
 * each {@code Task.output} must include a {@code type} with at least one {@code coding},
 * and each {@code coding} must define a {@code code} to identify the purpose of the output.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#TASK_OUTPUT_MISSING_CODE}.</p>
 */
public class FhirTaskOutputMissingTypeCodingCodeValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item using a custom message for a missing {@code type.coding.code} in {@code Task.output}.
     *
     * @param resourceFile  the file containing the FHIR Task resource
     * @param fhirReference the canonical URL or local reference identifying the resource
     * @param description   a custom human-readable description of the issue
     */
    public FhirTaskOutputMissingTypeCodingCodeValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.TASK_OUTPUT_MISSING_CODE,
                description);
    }

    /**
     * Constructs a validation item using the default message for a missing {@code type.coding.code}.
     *
     * @param resourceFile  the file containing the FHIR Task resource
     * @param fhirReference the canonical URL or local reference identifying the resource
     */
    public FhirTaskOutputMissingTypeCodingCodeValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task.output is missing <type><coding><code>.");
    }
}
