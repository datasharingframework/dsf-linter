package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that an Inclusive Gateway has multiple outgoing flows but the name is empty.
 * <p>
 * Corresponds to {@link LintingType#BPMN_INCLUSIVE_GATEWAY_HAS_MULTIPLE_OUTGOING_FLOWS_BUT_NAME_IS_EMPTY}.
 * </p>
 */
public class BpmnInclusiveGatewayHasMultipleOutgoingFlowsButNameIsEmptyLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new Lint Item for an Inclusive Gateway with multiple outgoing flows but empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnInclusiveGatewayHasMultipleOutgoingFlowsButNameIsEmptyLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.WARN,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "Inclusive Gateway has multiple outgoing flows but name is empty");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

