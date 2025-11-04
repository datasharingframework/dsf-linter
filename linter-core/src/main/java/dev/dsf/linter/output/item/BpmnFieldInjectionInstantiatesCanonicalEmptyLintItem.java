package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the instantiatesCanonical field injection is empty.
 * Corresponds to {@link LintingType#BPMN_FIELD_INJECTION_INSTANTIATES_CANONICAL_EMPTY}.
 */
public class BpmnFieldInjectionInstantiatesCanonicalEmptyLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new Lint Item for an empty instantiatesCanonical field injection.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnFieldInjectionInstantiatesCanonicalEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "instantiatesCanonical field injection is empty");
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom lint description
     */
    public BpmnFieldInjectionInstantiatesCanonicalEmptyLintItem(String elementId, File bpmnFile, String processId, String description) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
