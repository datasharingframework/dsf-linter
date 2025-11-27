package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a SubProcess has multi-instance configuration but is not set to asyncBefore=true.
 * <p>
 * Corresponds to {@link LintingType#BPMN_SUB_PROCESS_HAS_MULTI_INSTANCE_BUT_IS_NOT_ASYNC_BEFORE_TRUE}.
 * </p>
 */
public class BpmnSubProcessHasMultiInstanceButIsNotAsyncBeforeTrueLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new Lint Item for a SubProcess with multi-instance but asyncBefore not set to true.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnSubProcessHasMultiInstanceButIsNotAsyncBeforeTrueLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.ERROR,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "SubProcess has multi-instance but is not asyncBefore=true");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

