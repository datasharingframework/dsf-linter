package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the event name is empty.
 * Corresponds to {@link LintingType#BPMN_EVENT_NAME_EMPTY}.
 */
public class BpmnEventNameEmptyLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new Lint Item for an event with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnEventNameEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Event name is empty");
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom lint description
     */
    public BpmnEventNameEmptyLintItem(String elementId, File bpmnFile, String processId, String description) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription() {
        return description;
    }

}
