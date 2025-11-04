package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the implementation class in a message send event is empty.
 * Corresponds to {@link LintingType#BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_EMPTY}.
 */
public class BpmnMessageSendEventImplementationClassEmptyLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new Lint Item for a message send event with an empty implementation class.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageSendEventImplementationClassEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Message send event implementation class is empty");
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom lint description
     */
    public BpmnMessageSendEventImplementationClassEmptyLintItem(String elementId, File bpmnFile, String processId, String description) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
