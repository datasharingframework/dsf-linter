package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a Signal Intermediate Throw Event has an empty name.
 * <p>
 * Corresponds to {@link LintingType#BPMN_SIGNAL_INTERMEDIATE_THROW_EVENT_NAME_EMPTY}.
 * </p>
 */
public class BpmnSignalIntermediateThrowEventNameEmptyLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new Lint Item for a Signal Intermediate Throw Event with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnSignalIntermediateThrowEventNameEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.WARN,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "Signal Intermediate Throw Event name is empty");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

