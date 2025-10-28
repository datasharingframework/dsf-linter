package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that the implementation class for a service task is empty.
 * Corresponds to {@link LintingType#BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_EMPTY}.
 */
public class BpmnServiceTaskImplementationClassEmptyLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new validation item for a service task with an empty implementation class.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnServiceTaskImplementationClassEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Service task implementation class is empty");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnServiceTaskImplementationClassEmptyLintItem(String elementId, File bpmnFile, String processId, String description) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
