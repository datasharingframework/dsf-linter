package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a slice's {@code max} cardinality exceeds
 * the {@code max} value of the base element it slices.
 *
 * <p>This violates the FHIR profiling rule from §5.1.0.14 that requires
 * each {@code slice.max} to be less than or equal to the {@code max} of the base element.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#STRUCTURE_DEFINITION_SLICE_MAX_TOO_HIGH}.</p>
 */
public class FhirStructureDefinitionSliceMaxTooHighItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item for a slice maximum violation.
     *
     * @param resourceFile      the file containing the StructureDefinition
     * @param fhirReference     the canonical URL or local reference of the StructureDefinition
     * @param elementId         the base element being sliced (e.g. {@code Task.input})
     * @param baseMax           the {@code max} cardinality of the base element
     * @param offendingSliceId  the slice element whose {@code max} exceeds the base max
     * @param sliceMaxLabel     the offending slice's {@code max} value (e.g. "2" or "*")
     */
    public FhirStructureDefinitionSliceMaxTooHighItem(File resourceFile,
                                                      String fhirReference,
                                                      String elementId,
                                                      int baseMax,
                                                      String offendingSliceId,
                                                      String sliceMaxLabel)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_SLICE_MAX_TOO_HIGH,
                "Element '" + elementId + "' declares max=" + baseMax +
                        " but slice '" + offendingSliceId + "' allows up to " + sliceMaxLabel
        );
    }
}
