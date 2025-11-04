package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the message boundary event name is empty.
 * Corresponds to {@link LintingType#BPMN_MESSAGE_BOUNDARY_EVENT_NAME_EMPTY}.
 */
public class BpmnMessageBoundaryEventNameEmptyLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new Lint Item for a message boundary event with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageBoundaryEventNameEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Message boundary event name is empty");
    }

    /**
     * Constructs a new Lint Item for a message boundary event with an empty name using a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom lint description
     */
    public BpmnMessageBoundaryEventNameEmptyLintItem(String elementId, File bpmnFile, String processId, String description) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
