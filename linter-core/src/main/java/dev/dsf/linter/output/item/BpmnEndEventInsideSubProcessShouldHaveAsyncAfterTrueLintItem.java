package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that an End Event inside a SubProcess should have asyncAfter set to true.
 * <p>
 * Corresponds to {@link LintingType#BPMN_END_EVENT_INSIDE_SUB_PROCESS_SHOULD_HAVE_ASYNC_AFTER_TRUE}.
 * </p>
 */
public class BpmnEndEventInsideSubProcessShouldHaveAsyncAfterTrueLintItem extends BpmnElementLintItem {

    /**
     * Constructs a new Lint Item for an End Event inside a SubProcess that should have asyncAfter set to true.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnEndEventInsideSubProcessShouldHaveAsyncAfterTrueLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.WARN,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "End Event inside a SubProcess should have asyncAfter set to true");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

