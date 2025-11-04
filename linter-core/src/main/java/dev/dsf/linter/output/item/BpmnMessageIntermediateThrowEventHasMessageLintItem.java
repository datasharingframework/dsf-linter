package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a Message Intermediate Throw Event has a message.
 * Corresponds to {@link LintingType#BPMN_MESSAGE_INTERMEDIATE_THROW_EVENT_HAS_MESSAGE}.
 */
public class BpmnMessageIntermediateThrowEventHasMessageLintItem extends BpmnElementLintItem {
    private String message;

    /**
     * Constructs a new Lint Item for a Message Intermediate Throw Event that has a message.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageIntermediateThrowEventHasMessageLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.INFO, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Message Intermediate Throw Event does not have a message reference, as expected.");
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param messageName the messageName of the Event.
     */
    public BpmnMessageIntermediateThrowEventHasMessageLintItem(String elementId, File bpmnFile, String processId, String messageName) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Message Intermediate Throw Event has a message with name: " + messageName);
    }


    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] %s (elementId=%s, processId=%s, file=%s) -> Intermediate Throw Event contains a <messageEventDefinition> (\"Message\" is present).",
                getSeverity(),
                this.getClass().getSimpleName(),
                getElementId(),
                getProcessId(),
                getBpmnFile()
        );
    }
}
