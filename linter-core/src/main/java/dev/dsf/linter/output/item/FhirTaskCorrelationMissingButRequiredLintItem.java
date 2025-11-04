package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a {@code Task.input} element with the slice name
 * {@code correlation-key} is missing in the instance, although the corresponding slice
 * is defined with a minimum cardinality greater than zero in the associated {@code StructureDefinition}.
 *
 * <p>This violates the FHIR profiling rule that the minimum cardinality of each slice
 * must be met at the instance level.</p>
 *
 * <p>This issue corresponds to {@link LintingType#STRUCTURE_DEFINITION_TASK_INPUT_SLICE_CORRELATION_REQUIRED_BUT_MISSING}.</p>
 */
public class FhirTaskCorrelationMissingButRequiredLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item indicating that a {@code correlation-key} input
     * is missing, although the StructureDefinition declares it as required.
     *
     * @param resourceFile  the FHIR Task XML file being validated (may be {@code null})
     * @param fhirReference a logical reference or identifier used for reporting context
     * @param requiredMin   the declared minimum cardinality from the {@code StructureDefinition}
     */
    public FhirTaskCorrelationMissingButRequiredLintItem(File resourceFile, String fhirReference, int requiredMin) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_TASK_INPUT_SLICE_CORRELATION_REQUIRED_BUT_MISSING,
                "Missing input 'correlation-key', but StructureDefinition requires at least " + requiredMin + " occurrence(s).");
    }

    /**
     * Constructs a Lint Item using a custom message.
     *
     * @param resourceFile  the FHIR Task XML file being validated (may be {@code null})
     * @param fhirReference a logical reference or identifier used for reporting context
     * @param customMessage a custom error message to report
     */
    public FhirTaskCorrelationMissingButRequiredLintItem(File resourceFile, String fhirReference, String customMessage) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_TASK_INPUT_SLICE_CORRELATION_REQUIRED_BUT_MISSING,
                customMessage);
    }
}
