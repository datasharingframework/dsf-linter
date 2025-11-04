package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a {@code camunda:taskListener} on a {@code UserTask}
 * is missing the required {@code class} attribute.
 *
 * <p>
 * In Camunda BPMN extensions, each {@code camunda:taskListener} may define either a
 * {@code class}, {@code expression}, or {@code delegateExpression} to indicate how the listener
 * should be invoked at runtime. This Lint Item applies when the {@code class} attribute
 * is completely absent, which prevents further static linting of listener logic.
 * </p>
 *
 * <p>
 * Since use of expressions may be intentional, this issue is marked as a {@link LinterSeverity#WARN}
 * rather than an error. However, if class-based lint is expected, this serves as a notice
 * that such lint is being skipped.
 * </p>
 * <p>
 * Corresponds to {@link LintingType#BPMN_USER_TASK_LISTENER_MISSING_CLASS_ATTRIBUTE}.,
 * reused here for compatibility.
 */
public class BpmnUserTaskListenerMissingClassAttributeLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new warning for a {@code taskListener} element that does not declare the {@code class} attribute.
     *
     * @param elementId the BPMN element ID of the {@code UserTask}
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnUserTaskListenerMissingClassAttributeLintItem(String elementId, File bpmnFile, String processId) {
        super(LinterSeverity.WARN,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "TaskListener is missing the 'class' attribute – class-based linting skipped");
    }

    @Override
    public String getDescription() {
        return description;
    }
}
