package dev.dsf.linter.output.item;

import dev.dsf.linter.output.FloatingElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;


public class BpmnFloatingElementLintItem extends BpmnElementLintItem {
    private final LintingType lintingTypeOverride;
    private final FloatingElementType floatingElementType;

    /**
     * Constructs a floating-element Lint Item with a custom severity and a specific {@link FloatingElementType}.
     *
     * @param elementId              the BPMN element ID that caused the Lint issue
     * @param bpmnFile               the BPMN file being validated
     * @param processId              the ID or key of the process definition where the issue occurred
     * @param description            a human-readable message describing the Lint issue
     * @param lintingTypeOverride the validation type to associate with this issue
     * @param severityOverride       the severity to assign to this Lint issue
     * @param floatingElementType    the specific floating element type that categorizes this issue
     */
    public BpmnFloatingElementLintItem(
            String elementId,
            File bpmnFile,
            String processId,
            String description,
            LintingType lintingTypeOverride,
            LinterSeverity severityOverride,
            FloatingElementType floatingElementType) {
        super(severityOverride, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
        this.lintingTypeOverride = lintingTypeOverride;
        this.floatingElementType = floatingElementType;
    }

    /**
     * @return the human-readable description or reason for this floating-element validation
     */
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Gets the floating element type that categorizes this Lint issue, if available.
     *
     * @return the {@link FloatingElementType}, or {@code null} if not specified
     */
    public FloatingElementType getFloatingElementType() {
        return floatingElementType;
    }

    /**
     * Returns a string representation of this Lint Item including element ID, process ID, BPMN file,
     * description, validation type override, floating element type (if any), and severity.
     *
     * @return a string with detailed information about this Lint Item
     */
    @Override
    public String toString() {
        String details = String.format(
                "description='%s', lintingTypeOverride=%s, floatingElementType=%s",
                description,
                lintingTypeOverride,
                floatingElementType
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
