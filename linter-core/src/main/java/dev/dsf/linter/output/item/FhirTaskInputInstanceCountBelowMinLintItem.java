package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating that the number of {@code Task.input} elements in the instance
 * is lower than the minimum cardinality defined on the {@code Task.input} element in the associated
 * {@code StructureDefinition}.
 *
 * <p>This violates the FHIR profiling rule that the instance-level element count must satisfy
 * the base element’s minimum cardinality.</p>
 *
 * <p>This issue corresponds to {@link LintingType#STRUCTURE_DEFINITION_TASK_INPUT_INSTANCE_COUNT_BELOW_MIN}.</p>
 */
public class FhirTaskInputInstanceCountBelowMinLintItem extends FhirElementLintItem
{
    public FhirTaskInputInstanceCountBelowMinLintItem(File resourceFile, String fhirReference, int actualCount, int expectedMin)
    {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_TASK_INPUT_INSTANCE_COUNT_BELOW_MIN,
                "Task contains " + actualCount + " input(s), but at least " + expectedMin + " required.");
    }
}
