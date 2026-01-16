package dev.dsf.linter.output.item;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dsf.linter.output.FlowElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item for BPMN Flow Elements with additional flow element type information.
 * <p>
 * This class extends {@link BpmnElementLintItem} and adds the {@link FlowElementType}
 * which categorizes the specific flow element that has an issue.
 * </p>
 */
public class BpmnFlowElementLintItem extends BpmnElementLintItem {
    @JsonProperty("flowElementType")
    private final FlowElementType flowElementType;

    /**
     * Constructs a new BpmnFlowElementLintItem with all parameters.
     *
     * @param severity        the linting severity
     * @param type            the linting type/category
     * @param elementId       the BPMN element ID
     * @param bpmnFile        the BPMN file
     * @param processId       the process ID
     * @param description     the description
     * @param flowElementType the flow element type
     */
    public BpmnFlowElementLintItem(LinterSeverity severity,
                                   LintingType type,
                                   String elementId,
                                   File bpmnFile,
                                   String processId,
                                   String description,
                                   FlowElementType flowElementType) {
        super(severity, type, elementId, bpmnFile, processId, description);
        this.flowElementType = flowElementType;
    }

    /**
     * Factory method that uses the default message from LintingType.
     *
     * @param severity        the linting severity
     * @param type            the linting type
     * @param elementId       the BPMN element ID
     * @param bpmnFile        the BPMN file
     * @param processId       the process ID
     * @param flowElementType the flow element type
     * @return a new BpmnFlowElementLintItem
     */
    public static BpmnFlowElementLintItem of(LinterSeverity severity,
                                             LintingType type,
                                             String elementId,
                                             File bpmnFile,
                                             String processId,
                                             FlowElementType flowElementType) {
        return new BpmnFlowElementLintItem(severity, type, elementId, bpmnFile, processId,
                type.getDefaultMessageOrElse("BPMN flow element issue"), flowElementType);
    }

    /**
     * Backward compatible constructor (deprecated).
     *
     * @deprecated Use constructor with LintingType as second parameter
     */
    @Deprecated
    public BpmnFlowElementLintItem(String elementId, File bpmnFile, String processId,
                                   LintingType lintingTypeOverride,
                                   FlowElementType flowElementType) {
        this(LinterSeverity.WARN, lintingTypeOverride, elementId, bpmnFile, processId,
                "Flow element lint WARN", flowElementType);
    }



    /**
     * Returns the flow element type.
     *
     * @return the flow element type
     */
    public FlowElementType getFlowElementType() {
        return flowElementType;
    }

    /**
     * Returns the lint type (for backward compatibility).
     *
     * @return the linting type
     * @deprecated Use {@link #getType()} instead
     */
    @Deprecated
    public LintingType getLintTypeOverride() {
        return getType();
    }

    @Override
    public String toString() {
        return String.format(
                "[%s] %s (elementId=%s, processId=%s, file=%s, flowType=%s) : %s",
                getSeverity(),
                getType(),
                getElementId(),
                getProcessId(),
                getBpmnFile(),
                flowElementType,
                getDescription()
        );
    }
}
