package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the number of {@code Task.input} elements in the instance
 * exceeds the maximum cardinality defined on the {@code Task.input} element in the associated
 * {@code StructureDefinition}.
 *
 * <p>This violates the FHIR profiling rule that the instance must not exceed the base element’s
 * declared {@code max} cardinality.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#STRUCTURE_DEFINITION_TASK_INPUT_INSTANCE_COUNT_EXCEEDS_MAX}.</p>
 */
public class FhirTaskInputInstanceCountExceedsMaxItem extends FhirElementValidationItem
{
    public FhirTaskInputInstanceCountExceedsMaxItem(File resourceFile, String fhirReference, int actualCount, int allowedMax)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_TASK_INPUT_INSTANCE_COUNT_EXCEEDS_MAX,
                "Task contains " + actualCount + " input(s), but maximum allowed is " + allowedMax + ".");
    }
}
