package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

import dev.dsf.linter.output.LintingType;

/**
 * Lint Item indicating that a field injection is not provided as a string literal.
 * Corresponds to {@link LintingType#BPMN_FIELD_INJECTION_NOT_STRING_LITERAL}.
 */
public class BpmnFieldInjectionNotStringLiteralLintItem extends BpmnElementLintItem {
    private final String fieldName;

    /**
     * Constructs a new Lint Item indicating that a field injection is not a string literal.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param fieldName the name of the field injection
     */
    public BpmnFieldInjectionNotStringLiteralLintItem(String elementId, File bpmnFile, String processId, String fieldName, String description) {
        super(LinterSeverity.INFO, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
        this.fieldName = fieldName;
    }

    /**
     * Returns the field name for which the literal check failed.
     *
     * @return the field name
     */
    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("%s, fieldName=%s", super.toString(), fieldName);
    }
}