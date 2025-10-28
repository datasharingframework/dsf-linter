package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a BPMN end event or BPMN IntermediateThroeEvent does not have a proper interface class implementation.
 * Corresponds to {@link LintingType#BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING}.
 */
public class BpmnEndOrIntermediateThrowEventMissingInterfaceLintItem extends BpmnElementLintItem {
    private final String className;

    /**
     * Constructs a new validation item for an end event with no proper interface class implementation.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the class name that does not implement the required interface
     */
    public BpmnEndOrIntermediateThrowEventMissingInterfaceLintItem(String elementId, File bpmnFile, String processId, String className) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "End event has no proper interface class implementation: " + className);
        this.className = className;
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param className   the class name that does not implement the required interface
     * @param description the custom validation description
     */
    public BpmnEndOrIntermediateThrowEventMissingInterfaceLintItem(String elementId, File bpmnFile, String processId, String className, String description) {
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