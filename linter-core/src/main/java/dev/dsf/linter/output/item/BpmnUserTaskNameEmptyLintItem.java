package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a User Task has an empty name.
 * <p>
 * Corresponds to {@link LintingType#BPMN_USER_TASK_NAME_EMPTY}.
 * </p>
 */
public class BpmnUserTaskNameEmptyLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new Lint Item for a User Task with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnUserTaskNameEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.WARN,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "User Task name is empty");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

