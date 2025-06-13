package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Informational validation item indicating that the total minimum cardinality (sum of {@code slice.min})
 * exceeds the declared {@code min} of the base element in a StructureDefinition.
 *
 * <p>While this does not violate a MUST rule, it is discouraged by the FHIR profiling guidance (§5.1.0.14),
 * which recommends that the sum of {@code slice.min} values SHOULD be less than or equal to the
 * {@code min} of the base element.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#STRUCTURE_DEFINITION_SLICE_MIN_SUM_ABOVE_BASE_MIN,}.</p>
 */
public class FhirStructureDefinitionSliceMinSumAboveBaseMinItem extends FhirElementValidationItem
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
    public FhirStructureDefinitionSliceMinSumAboveBaseMinItem(File resourceFile,
                                                     String fhirReference,
                                                     String elementId,
                                                     int baseMin,
                                                     int sumOfSliceMin)
    {
        super(
                ValidationSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_SLICE_MIN_SUM_ABOVE_BASE_MIN,
                "Element '" + elementId + "' declares min=" + baseMin +
                        " but the sum of slice.min values is only " + sumOfSliceMin
        );
    }
}
