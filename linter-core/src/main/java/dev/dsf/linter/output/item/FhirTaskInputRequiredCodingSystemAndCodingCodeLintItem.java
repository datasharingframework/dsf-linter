package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a {@code Task.input} element is missing either the
 * {@code type.coding.system} or {@code type.coding.code} sub-element.
 *
 * <p>According to the FHIR specification and the DSF {@code task-base} profile,
 * each {@code Task.input} must include a {@code type} with at least one {@code coding},
 * and each {@code coding} must specify a {@code system} and a {@code code}.</p>
 *
 * <p>This Lint issue corresponds to {@link LintingType#TASK_INPUT_MISSING_SYSTEM_OR_CODE}.</p>
 */
public class FhirTaskInputRequiredCodingSystemAndCodingCodeLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item indicating a missing {@code system} or {@code code} in {@code Task.input.type.coding}.
     *
     * @param resourceFile  the file containing the Task resource
     * @param fhirReference the canonical URL or local reference to the resource
     * @param description   a custom message describing the issue
     */
    public FhirTaskInputRequiredCodingSystemAndCodingCodeLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.TASK_INPUT_MISSING_SYSTEM_OR_CODE,
                description);
    }

    /**
     * Constructs a Lint Item with a default message indicating missing system or code.
     *
     * @param resourceFile  the file containing the Task resource
     * @param fhirReference the canonical URL or local reference to the resource
     */
    public FhirTaskInputRequiredCodingSystemAndCodingCodeLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "A <Task.input> element is missing <type><coding><system> or <code>.");
    }
}
