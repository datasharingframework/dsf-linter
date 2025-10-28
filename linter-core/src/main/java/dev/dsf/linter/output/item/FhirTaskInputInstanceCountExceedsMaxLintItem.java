package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating that the number of {@code Task.input} elements in the instance
 * exceeds the maximum cardinality defined on the {@code Task.input} element in the associated
 * {@code StructureDefinition}.
 *
 * <p>This violates the FHIR profiling rule that the instance must not exceed the base element’s
 * declared {@code max} cardinality.</p>
 *
 * <p>This issue corresponds to {@link LintingType#STRUCTURE_DEFINITION_TASK_INPUT_INSTANCE_COUNT_EXCEEDS_MAX}.</p>
 */
public class FhirTaskInputInstanceCountExceedsMaxLintItem extends FhirElementLintItem
{
    public FhirTaskInputInstanceCountExceedsMaxLintItem(File resourceFile, String fhirReference, int actualCount, int allowedMax)
    {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_TASK_INPUT_INSTANCE_COUNT_EXCEEDS_MAX,
                "Task contains " + actualCount + " input(s), but maximum allowed is " + allowedMax + ".");
    }
}
