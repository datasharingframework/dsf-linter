package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code Task.input} element is missing either the
 * {@code type.coding.system} or {@code type.coding.code} sub-element.
 *
 * <p>According to the FHIR specification and the DSF {@code task-base} profile,
 * each {@code Task.input} must include a {@code type} with at least one {@code coding},
 * and each {@code coding} must specify a {@code system} and a {@code code}.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#TASK_INPUT_MISSING_SYSTEM_OR_CODE}.</p>
 */
public class FhirTaskInputRequiredCodingSystemAndCodingCodeValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating a missing {@code system} or {@code code} in {@code Task.input.type.coding}.
     *
     * @param resourceFile  the file containing the Task resource
     * @param fhirReference the canonical URL or local reference to the resource
     * @param description   a custom message describing the issue
     */
    public FhirTaskInputRequiredCodingSystemAndCodingCodeValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.TASK_INPUT_MISSING_SYSTEM_OR_CODE,
                description);
    }

    /**
     * Constructs a validation item with a default message indicating missing system or code.
     *
     * @param resourceFile  the file containing the Task resource
     * @param fhirReference the canonical URL or local reference to the resource
     */
    public FhirTaskInputRequiredCodingSystemAndCodingCodeValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "A <Task.input> element is missing <type><coding><system> or <code>.");
    }
}
