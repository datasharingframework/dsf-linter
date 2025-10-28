package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Informational linting item indicating that the total minimum cardinality (sum of {@code slice.min})
 * exceeds the declared {@code min} of the base element in a StructureDefinition.
 *
 * <p>While this does not violate a MUST rule, it is discouraged by the FHIR profiling guidance (§5.1.0.14),
 * which recommends that the sum of {@code slice.min} values SHOULD be less than or equal to the
 * {@code min} of the base element.</p>
 *
 * <p>This issue corresponds to {@link LintingType#STRUCTURE_DEFINITION_SLICE_MIN_SUM_ABOVE_BASE_MIN,}.</p>
 */
public class FhirStructureDefinitionSliceMinSumAboveBaseMinLintItem extends FhirElementLintItem
{
    /**
     * Constructs a linting item for a slice minimum violation.
     *
     * @param resourceFile      the file containing the StructureDefinition
     * @param fhirReference     the canonical URL or local reference of the StructureDefinition
     * @param elementId         the element ID of the sliced base element (e.g. {@code Task.input})
     * @param baseMin           the {@code min} value declared on the base element
     * @param sumOfSliceMin     the total of all slice {@code min} values
     */
    public FhirStructureDefinitionSliceMinSumAboveBaseMinLintItem(File resourceFile,
                                                                  String fhirReference,
                                                                  String elementId,
                                                                  int baseMin,
                                                                  int sumOfSliceMin)
    {
        super(
                LinterSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_SLICE_MIN_SUM_ABOVE_BASE_MIN,
                "Element '" + elementId + "' declares min=" + baseMin +
                        " but the sum of slice.min values is only " + sumOfSliceMin
        );
    }
}
