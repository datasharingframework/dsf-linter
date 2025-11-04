package dev.dsf.linter.output.item;

import dev.dsf.linter.output.FlowElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating an issue with a BPMN Flow Element.
 */
public class BpmnFlowElementLintItem extends BpmnElementLintItem {
    private final LintingType lintingTypeOverride;
    private final FlowElementType flowElementType;

    /**
     * Constructs a new Lint Item for a BPMN Flow Element with a default error description.
     * The default severity is set to {@link LinterSeverity#WARN}.
     *
     * @param elementId              the BPMN element ID
     * @param bpmnFile               the BPMN file being validated
     * @param processId              the process definition ID or key
     * @param lintingTypeOverride the validation type to associate with this issue
     * @param flowElementType        the specific flow element type that categorizes this issue
     */
    public BpmnFlowElementLintItem(String elementId, File bpmnFile, String processId,
                                   LintingType lintingTypeOverride,
                                   FlowElementType flowElementType) {
        super(LinterSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Flow element validation WARN");
        this.lintingTypeOverride = lintingTypeOverride;
        this.flowElementType = flowElementType;
    }

    /**
     * Constructs a new Lint Item for a BPMN Flow Element with a custom description.
     *
     * @param elementId              the BPMN element ID
     * @param bpmnFile               the BPMN file being validated
     * @param processId              the process definition ID or key
     * @param description            the custom lint description
     * @param lintingTypeOverride the validation type to associate with this issue
     * @param severityOverride       the severity to assign to this Lint issue
     * @param flowElementType        the specific flow element type that categorizes this issue
     */
    public BpmnFlowElementLintItem(String elementId, File bpmnFile, String processId, String description,
                                   LintingType lintingTypeOverride,
                                   LinterSeverity severityOverride,
                                   FlowElementType flowElementType) {
        super(severityOverride, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
        this.lintingTypeOverride = lintingTypeOverride;
        this.flowElementType = flowElementType;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Returns the validation type override for this flow element Lint Item.
     *
     * @return the {@link LintingType} associated with this Lint issue.
     */
    public LintingType getLintTypeOverride() {
        return lintingTypeOverride;
    }

    /**
     * Returns the flow element type that categorizes this Lint issue.
     *
     * @return the {@link FlowElementType} if specified, or {@code null} if not.
     */
    public FlowElementType getFlowElementType() {
        return flowElementType;
    }

    @Override
    public String toString() {
        String details = String.format(
                "description='%s', lintingTypeOverride=%s, flowElementType=%s",
                description,
                lintingTypeOverride,
                flowElementType
        );

        return String.format(
                "[%s] %s (elementId=%s, processId=%s, file=%s) : %s",
                getSeverity(),
                this.getClass().getSimpleName(),
                getElementId(),
                getProcessId(),
                getBpmnFile(),
                details
        );
    }
}
