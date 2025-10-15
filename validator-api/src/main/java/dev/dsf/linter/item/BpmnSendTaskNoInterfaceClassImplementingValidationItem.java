package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;
import java.io.File;

/**
 * Validation item indicating that a BPMN send task does not have a proper interface class implementation.
 * Corresponds to {@link ValidationType#BPMN_SEND_TASK_NO_INTERFACE_CLASS_IMPLEMENTING}.
 */
public class BpmnSendTaskNoInterfaceClassImplementingValidationItem extends BpmnElementValidationItem
{
    private final String className;

    /**
     * Constructs a new validation item for a send task with no proper interface class implementation.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the class name that does not implement the required interface
     */
    public BpmnSendTaskNoInterfaceClassImplementingValidationItem(String elementId, File bpmnFile, String processId, String className)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Send task has no proper interface class implementation: " + className);
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
    public BpmnSendTaskNoInterfaceClassImplementingValidationItem(String elementId, File bpmnFile, String processId, String className, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
        this.className = className;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    public String getClassName()
    {
        return className;
    }

    @Override
    public String toString()
    {
        return String.format(
                "%s, className=%s",
                super.toString(),
                className
        );
    }
}