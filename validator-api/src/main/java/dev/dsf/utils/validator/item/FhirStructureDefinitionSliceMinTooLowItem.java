package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the total minimum cardinality (sum of {@code slice.min})
 * is lower than the declared {@code min} of the base element in a StructureDefinition.
 *
 * <p>This issue violates the FHIR profiling rule defined in §5.1.0.14:
 * the sum of {@code min} values of all slices must be greater than or equal to the
 * {@code min} of the base element.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#STRUCTURE_DEFINITION_SLICE_MIN_TOO_LOW}.</p>
 */
public class FhirStructureDefinitionSliceMinTooLowItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item for a slice minimum violation.
     *
     * @param resourceFile      the file containing the StructureDefinition
     * @param fhirReference     the canonical URL or local reference of the StructureDefinition
     * @param elementId         the element ID of the sliced base element (e.g. {@code Task.input})
     * @param baseMin           the {@code min} value declared on the base element
     * @param sumOfSliceMin     the total of all slice {@code min} values
     */
    public FhirStructureDefinitionSliceMinTooLowItem(File resourceFile,
                                                     String fhirReference,
                                                     String elementId,
                                                     int baseMin,
                                                     int sumOfSliceMin)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_SLICE_MIN_TOO_LOW,
                "Element '" + elementId + "' declares min=" + baseMin +
                        " but the sum of slice.min values is only " + sumOfSliceMin
        );
    }
}
