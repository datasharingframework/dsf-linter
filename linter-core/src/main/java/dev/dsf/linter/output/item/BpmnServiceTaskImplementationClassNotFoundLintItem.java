package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the implementation class for a service task was not found.
 * Corresponds to {@link LintingType#BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_FOUND}.
 */
public class BpmnServiceTaskImplementationClassNotFoundLintItem extends BpmnElementLintItem {
    private final String className;

    /**
     * Constructs a new Lint Item for a service task where the implementation class was not found.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the implementation class name that was not found
     */
    public BpmnServiceTaskImplementationClassNotFoundLintItem(String elementId, File bpmnFile, String processId, String className) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Service task implementation class not found: " + className);
        this.className = className;
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param className   the implementation class name that was not found
     * @param description the custom lint description
     */
    public BpmnServiceTaskImplementationClassNotFoundLintItem(String elementId, File bpmnFile, String processId, String className, String description) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
        this.className = className;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public String toString() {
        return String.format(
                "%s, missingClassName=%s",
                super.toString(),
                className
        );
    }
}
