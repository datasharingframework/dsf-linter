package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Lint Item: Unknown Field Injection
 * LintingType: BPMN_UNKNOWN_FIELD_INJECTION
 *
 * <p>This Lint Item is used to indicate that an unknown field injection has been encountered.
 * Only the fields "profile", "messageName", and "instantiatesCanonical" are allowed.
 * Any field with a different name results in This lint error.</p>
 * Corresponds to {@link LintingType#BPMN_UNKNOWN_FIELD_INJECTION}.
 */
public class BpmnUnknownFieldInjectionLintItem extends BpmnElementLintItem {
    private final String fieldName;

    /**
     * Constructs a new Lint Item for an unknown field injection with a default description.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param fieldName the name of the unknown field
     */
    public BpmnUnknownFieldInjectionLintItem(String elementId, File bpmnFile, String processId, String fieldName) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId,
                "Unknown field injection encountered: " + fieldName);
        this.fieldName = fieldName;
    }

    /**
     * Constructs a new Lint Item for an unknown field injection with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param fieldName   the name of the unknown field
     * @param description the custom lint description
     */
    public BpmnUnknownFieldInjectionLintItem(String elementId, File bpmnFile, String processId, String fieldName, String description) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("%s, unknownField=%s", super.toString(), fieldName);
    }
}
