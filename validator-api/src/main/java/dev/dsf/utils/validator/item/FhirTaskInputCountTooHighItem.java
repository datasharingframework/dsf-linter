package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the number of {@code Task.input} elements exceeds
 * the maximum defined in the associated {@code StructureDefinition}.
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_INPUT_COUNT_TOO_HIGH}.</p>
 */
public class FhirTaskInputCountTooHighItem extends FhirElementValidationItem
{
    public FhirTaskInputCountTooHighItem(File resourceFile, String fhirReference, int actualCount, int allowedMax)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_INPUT_COUNT_TOO_HIGH,
                "Task contains " + actualCount + " input(s), but maximum allowed is " + allowedMax + ".");
    }
}
