package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a {@code camunda:taskListener} class
 * specified on a {@code UserTask} does not extend or implement the required class or interface.
 *
 * <p>
 * In Camunda BPMN extensions, {@code camunda:taskListener} classes are expected to implement
 * specific interfaces (such as {@code TaskListener}) or extend certain base classes to function
 * properly at runtime. This validation item is created when a listener class is found but does
 * not conform to the expected type hierarchy.
 * </p>
 *
 * <p>
 * This is marked as an {@link LinterSeverity#ERROR} since the listener will not function
 * correctly at runtime without implementing the required interface or extending the required class.
 * </p>
 * <p>
 * Corresponds to {@link LintingType#BPMN_USER_TASK_LISTENER_NOT_EXTENDING_OR_IMPLEMENTING_REQUIRED_CLASS}.
 */
public class BpmnUserTaskListenerNotExtendingOrImplementingRequiredClassLintItem extends BpmnElementLintItem {
    private final String className;
    private final String requiredType;

    /**
     * Constructs a new validation item for a listener class that does not extend or implement the required type.
     *
     * @param elementId    the BPMN element ID of the {@code UserTask}
     * @param bpmnFile     the BPMN file being validated
     * @param processId    the process definition ID or key
     * @param className    the fully qualified class name of the listener
     * @param requiredType the required class or interface name that should be extended/implemented
     */
    public BpmnUserTaskListenerNotExtendingOrImplementingRequiredClassLintItem(String elementId, File bpmnFile, String processId, String className, String requiredType) {
        super(LinterSeverity.ERROR,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "UserTask listener class '" + className + "' does not extend or implement required type '" + requiredType + "'");
        this.className = className;
        this.requiredType = requiredType;
    }

    /**
     * @return the name of the listener class that does not conform to requirements
     */
    public String getClassName() {
        return className;
    }

    /**
     * @return the required class or interface name that should be extended/implemented
     */
    public String getRequiredType() {
        return requiredType;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("%s, className=%s, requiredType=%s", super.toString(), className, requiredType);
    }
}

