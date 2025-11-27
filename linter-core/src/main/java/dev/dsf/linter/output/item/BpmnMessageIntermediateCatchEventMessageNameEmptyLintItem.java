package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the message name in a message intermediate catch event is empty.
 * Corresponds to {@link LintingType#BPMN_MESSAGE_INTERMEDIATE_CATCH_EVENT_MESSAGE_NAME_EMPTY}.
 */
public class BpmnMessageIntermediateCatchEventMessageNameEmptyLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new Lint Item for a message intermediate catch event with an empty message name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageIntermediateCatchEventMessageNameEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Message intermediate catch event message name is empty");
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom lint description
     */
    public BpmnMessageIntermediateCatchEventMessageNameEmptyLintItem(String elementId, File bpmnFile, String processId, String description) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription() {
        return description;
    }
}

