package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a Signal Intermediate Throw Event has an empty signal definition.
 * <p>
 * Corresponds to {@link LintingType#BPMN_SIGNAL_INTERMEDIATE_THROW_EVENT_SIGNAL_EMPTY}.
 * </p>
 */
public class BpmnSignalIntermediateThrowEventSignalEmptyLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new Lint Item for a Signal Intermediate Throw Event with an empty signal.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnSignalIntermediateThrowEventSignalEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.ERROR,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "Signal is empty in Signal Intermediate Throw Event");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

