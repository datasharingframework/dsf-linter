package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the total minimum cardinality (sum of {@code slice.min})
 * exceeds the declared {@code max} of the base element in a StructureDefinition.
 *
 * <p>This issue violates the FHIR profiling rule defined in §5.1.0.14:
 * the sum of {@code min} values of all slices must be less than or equal to the
 * {@code max} value of the base element.</p>
 *
 * <p>This constraint ensures that the combined minimum cardinality of all defined slices
 * never requires more instances than the base element allows at most.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#STRUCTURE_DEFINITION_SLICE_MIN_SUM_EXCEEDS_MAX}.</p>
 */
public class FhirStructureDefinitionSliceMinSumExceedsMaxItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item for a violation where the total slice minimum cardinality
     * exceeds the declared maximum of the base element.
     *
     * @param resourceFile   the file containing the StructureDefinition
     * @param fhirReference  the canonical URL or local reference of the StructureDefinition
     * @param elementId      the element ID of the sliced base element (e.g. {@code Task.input})
     * @param baseMax        the {@code max} value declared on the base element
     * @param sumOfSliceMin  the total of all slice {@code min} values
     */
    public FhirStructureDefinitionSliceMinSumExceedsMaxItem(File resourceFile,
                                                            String fhirReference,
                                                            String elementId,
                                                            int baseMax,
                                                            int sumOfSliceMin)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_SLICE_MIN_SUM_EXCEEDS_MAX,
                "Element '" + elementId + "' declares max=" + baseMax +
                        " but the sum of slice.min values is " + sumOfSliceMin +
                        ", which exceeds the maximum allowed"
        );
    }
}
