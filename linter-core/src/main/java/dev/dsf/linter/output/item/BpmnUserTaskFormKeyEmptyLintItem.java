package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a User Task has an empty formKey.
 * <p>
 * Corresponds to {@link LintingType#BPMN_USER_TASK_FORM_KEY_EMPTY}.
 * </p>
 */
public class BpmnUserTaskFormKeyEmptyLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new Lint Item for a User Task with an empty formKey.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnUserTaskFormKeyEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.ERROR,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "User Task formKey is empty");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

