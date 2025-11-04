package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the service task name is empty.
 * Corresponds to {@link LintingType#BPMN_SERVICE_TASK_NAME_EMPTY}.
 */
public class BpmnServiceTaskNameEmptyLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new Lint Item for a service task with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnServiceTaskNameEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Service task name is empty");
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom lint description
     */
    public BpmnServiceTaskNameEmptyLintItem(String elementId, File bpmnFile, String processId, String description) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
