package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a {@code camunda:executionListener} class
 * specified on a BPMN element does not implement the required interface.
 *
 * <p>
 * In Camunda BPMN extensions, {@code camunda:executionListener} classes are expected to implement
 * the {@code ExecutionListener} interface to function properly at runtime. This Lint Item is created
 * when a listener class is found but does not implement the required interface.
 * </p>
 *
 * <p>
 * This is marked as an {@link LinterSeverity#ERROR} since the listener will not function
 * correctly at runtime without implementing the required interface.
 * </p>
 * <p>
 * Corresponds to {@link LintingType#BPMN_EXECUTION_LISTENER_CLASS_NOT_FOUND} (reused for consistency).
 * </p>
 */
public class BpmnExecutionListenerNotImplementingRequiredInterfaceLintItem extends BpmnElementLintItem {
    private final String className;

    /**
     * Constructs a new Lint Item for an execution listener class that does not implement the required interface.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the fully qualified class name of the listener
     * @param description the error description
     */
    public BpmnExecutionListenerNotImplementingRequiredInterfaceLintItem(
            String elementId, File bpmnFile, String processId, String className, String description) {
        super(LinterSeverity.ERROR,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                description);
        this.className = className;
    }

    /**
     * @return the name of the listener class that does not implement the required interface
     */
    public String getClassName() {
        return className;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("%s, className=%s", super.toString(), className);
    }
}

