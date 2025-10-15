package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the number of occurrences of a specific {@code Task.input}
 * slice (identified by code) is lower than the minimum cardinality defined for that slice
 * in the associated {@code StructureDefinition}.
 *
 * <p>This violates the FHIR profiling rule that requires each slice to appear at least
 * as often as its declared {@code min} cardinality.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#STRUCTURE_DEFINITION_TASK_INPUT_SLICE_COUNT_BELOW_MIN}.</p>
 */
public class FhirTaskInputSliceCountBelowSliceMinItem extends FhirElementValidationItem
{
    public FhirTaskInputSliceCountBelowSliceMinItem(File resourceFile, String fhirReference, String sliceCode, int actualCount, int expectedMin)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_TASK_INPUT_SLICE_COUNT_BELOW_MIN,
                "Input slice '" + sliceCode + "' occurs " + actualCount + "×, but minimum required is " + expectedMin + ".");
    }
}