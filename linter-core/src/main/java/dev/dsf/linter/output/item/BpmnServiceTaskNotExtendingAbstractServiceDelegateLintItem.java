package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the implementation class for a service task does not extend AbstractServiceDelegate.
 * Corresponds to {@link LintingType#BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_SERVICE_DELEGATE}.
 */
public class BpmnServiceTaskNotExtendingAbstractServiceDelegateLintItem extends BpmnElementLintItem {
    private final String className;

    /**
     * Constructs a new Lint Item for a service task where the implementation class does not extend AbstractServiceDelegate.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the implementation class name that does not extend AbstractServiceDelegate
     */
    public BpmnServiceTaskNotExtendingAbstractServiceDelegateLintItem(String elementId, File bpmnFile, String processId, String className) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Service task implementation class does not extend AbstractServiceDelegate: " + className);
        this.className = className;
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param className   the implementation class name that does not extend AbstractServiceDelegate
     * @param description the custom lint description
     */
    public BpmnServiceTaskNotExtendingAbstractServiceDelegateLintItem(String elementId, File bpmnFile, String processId, String className, String description) {
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
                "%s, className=%s",
                super.toString(),
                className
        );
    }
}

