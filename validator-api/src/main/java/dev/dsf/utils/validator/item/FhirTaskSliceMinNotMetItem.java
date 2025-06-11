package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a specific input slice (identified by code) is present fewer times
 * than the minimum required by the profile.
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_SLICE_MIN_NOT_MET}.</p>
 */
public class FhirTaskSliceMinNotMetItem extends FhirElementValidationItem
{
    public FhirTaskSliceMinNotMetItem(File resourceFile, String fhirReference, String sliceCode, int actualCount, int expectedMin)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_SLICE_MIN_NOT_MET,
                "Input slice '" + sliceCode + "' occurs " + actualCount + "×, but minimum required is " + expectedMin + ".");
    }
}
