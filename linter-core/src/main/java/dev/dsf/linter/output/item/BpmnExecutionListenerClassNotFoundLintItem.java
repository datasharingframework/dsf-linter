package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a {@code camunda:executionListener} class
 * specified on a BPMN element could not be found on the classpath.
 *
 * <p>
 * The linter attempted to resolve the listener class name using the provided project
 * root and a custom class loader. If the class is not available (e.g., missing dependency,
 * build error, or typo), this issue is recorded.
 * </p>
 * <p>
 * Corresponds to {@link LintingType#BPMN_EXECUTION_LISTENER_CLASS_NOT_FOUND}.
 * </p>
 */
public class BpmnExecutionListenerClassNotFoundLintItem extends BpmnElementLintItem {
    private final String className;

    /**
     * Constructs a new Lint Item for an execution listener class that could not be found.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the fully qualified class name that was not found
     */
    public BpmnExecutionListenerClassNotFoundLintItem(String elementId, File bpmnFile, String processId, String className) {
        super(LinterSeverity.ERROR,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "Execution listener class not found: '" + className + "'");
        this.className = className;
    }

    /**
     * @return the name of the class that could not be found
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

