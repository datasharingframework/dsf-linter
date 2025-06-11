package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the number of {@code Task.input} elements is less than
 * the minimum defined in the associated {@code StructureDefinition}.
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_INPUT_COUNT_TOO_LOW}.</p>
 */
public class FhirTaskInputCountTooLowItem extends FhirElementValidationItem
{
    public FhirTaskInputCountTooLowItem(File resourceFile, String fhirReference, int actualCount, int expectedMin)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_INPUT_COUNT_TOO_LOW,
                "Task contains " + actualCount + " input(s), but at least " + expectedMin + " required.");
    }
}
