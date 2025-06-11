package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a specific input slice (identified by code) appears
 * more times than allowed by its defined maximum in the profile.
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_SLICE_MAX_EXCEEDED}.</p>
 */
public class FhirTaskSliceMaxExceededItem extends FhirElementValidationItem
{
    public FhirTaskSliceMaxExceededItem(File resourceFile, String fhirReference, String sliceCode, int actualCount, int allowedMax)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_SLICE_MAX_EXCEEDED,
                "Input slice '" + sliceCode + "' occurs " + actualCount + "×, but maximum allowed is " + allowedMax + ".");
    }
}
