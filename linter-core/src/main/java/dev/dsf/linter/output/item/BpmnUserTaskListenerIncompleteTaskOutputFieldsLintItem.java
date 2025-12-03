package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Lint Item indicating that taskOutput field injections are incomplete.
 * <p>
 * In API v2, if any of the three taskOutput fields (taskOutputSystem, taskOutputCode, taskOutputVersion)
 * is set, all three must be set. This ensures that the output parameter can be properly created
 * with a complete Coding reference.
 * </p>
 */
public class BpmnUserTaskListenerIncompleteTaskOutputFieldsLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new Lint Item for incomplete taskOutput fields.
     *
     * @param elementId the BPMN element ID of the {@code UserTask}
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnUserTaskListenerIncompleteTaskOutputFieldsLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.ERROR,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "If taskOutputSystem, taskOutputCode, or taskOutputVersion is set, all three must be set");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

