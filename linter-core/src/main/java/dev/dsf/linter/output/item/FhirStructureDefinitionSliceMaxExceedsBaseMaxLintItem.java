package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating that a slice's {@code max} cardinality exceeds
 * the {@code max} value of the base element it slices.
 *
 * <p>This violates the FHIR profiling rule from §5.1.0.14 that requires
 * each {@code slice.max} to be less than or equal to the {@code max} of the base element.</p>
 *
 * <p>This issue corresponds to {@link LintingType#STRUCTURE_DEFINITION_SLICE_MAX_TOO_HIGH}.</p>
 */
public class FhirStructureDefinitionSliceMaxExceedsBaseMaxLintItem extends FhirElementLintItem
{
    /**
     * Constructs a linting item for a slice maximum violation.
     *
     * @param resourceFile      the file containing the StructureDefinition
     * @param fhirReference     the canonical URL or local reference of the StructureDefinition
     * @param elementId         the base element being sliced (e.g. {@code Task.input})
     * @param baseMax           the {@code max} cardinality of the base element
     * @param offendingSliceId  the slice element whose {@code max} exceeds the base max
     * @param sliceMaxLabel     the offending slice's {@code max} value (e.g. "2" or "*")
     */
    public FhirStructureDefinitionSliceMaxExceedsBaseMaxLintItem(File resourceFile,
                                                                 String fhirReference,
                                                                 String elementId,
                                                                 int baseMax,
                                                                 String offendingSliceId,
                                                                 String sliceMaxLabel)
    {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_SLICE_MAX_TOO_HIGH,
                "Element '" + elementId + "' declares max=" + baseMax +
                        " but slice '" + offendingSliceId + "' allows up to " + sliceMaxLabel
        );
    }
}
