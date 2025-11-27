package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a User Task formKey is not an external form.
 * <p>
 * Corresponds to {@link LintingType#BPMN_USER_TASK_FORM_KEY_IS_NOT_AN_EXTERNAL_FORM}.
 * </p>
 */
public class BpmnUserTaskFormKeyIsNotAnExternalFormLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new Lint Item for a User Task with a formKey that is not an external form.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnUserTaskFormKeyIsNotAnExternalFormLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.ERROR,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "User Task formKey is not an external form");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

