package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a start event is not part of a subprocess.
 * Corresponds to {@link LintingType#BPMN_START_EVENT_NOT_PART_OF_SUB_PROCESS}.
 */
public class BpmnStartEventNotPartOfSubProcessLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new validation item for a start event that is not part of a subprocess.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnStartEventNotPartOfSubProcessLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Start event is not part of a subprocess and has no name.");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnStartEventNotPartOfSubProcessLintItem(String elementId, File bpmnFile, String processId, String description) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
