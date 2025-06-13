package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the number of occurrences of a specific {@code Task.input}
 * slice (identified by code) exceeds the maximum cardinality defined for that slice
 * in the associated {@code StructureDefinition}.
 *
 * <p>This violates the FHIR profiling rule that prohibits exceeding the declared
 * {@code max} cardinality for any defined slice.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#STRUCTURE_DEFINITION_TASK_INPUT_SLICE_COUNT_EXCEEDS_MAX}.</p>
 */
public class FhirTaskInputSliceCountExceedsSliceMaxItem extends FhirElementValidationItem
{
    public FhirTaskInputSliceCountExceedsSliceMaxItem(File resourceFile, String fhirReference, String sliceCode, int actualCount, int allowedMax)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_TASK_INPUT_SLICE_COUNT_EXCEEDS_MAX,
                "Input slice '" + sliceCode + "' occurs " + actualCount + "×, but maximum allowed is " + allowedMax + ".");
    }
}
