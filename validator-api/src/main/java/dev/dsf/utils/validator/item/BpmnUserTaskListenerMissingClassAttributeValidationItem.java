package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code camunda:taskListener} on a {@code UserTask}
 * is missing the required {@code class} attribute.
 *
 * <p>
 * In Camunda BPMN extensions, each {@code camunda:taskListener} may define either a
 * {@code class}, {@code expression}, or {@code delegateExpression} to indicate how the listener
 * should be invoked at runtime. This validation item applies when the {@code class} attribute
 * is completely absent, which prevents further static validation of listener logic.
 * </p>
 *
 * <p>
 * Since use of expressions may be intentional, this issue is marked as a {@link ValidationSeverity#WARN}
 * rather than an error. However, if class-based validation is expected, this serves as a notice
 * that such validation is being skipped.
 * </p>
 *
 * Corresponds to {@link ValidationType#BPMN_USER_TASK_LISTENER_MISSING_CLASS_ATTRIBUTE}.,
 * reused here for compatibility.
 */
public class BpmnUserTaskListenerMissingClassAttributeValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new warning for a {@code taskListener} element that does not declare the {@code class} attribute.
     *
     * @param elementId the BPMN element ID of the {@code UserTask}
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnUserTaskListenerMissingClassAttributeValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "TaskListener is missing the 'class' attribute – class-based validation skipped");
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
