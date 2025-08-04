package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that a BPMN service task does not have a proper interface class implementation.
 * Corresponds to {@link ValidationType#BPMN_SERVICE_TASK_NO_INTERFACE_CLASS_IMPLEMENTING}.
 */
public class BpmnServiceTaskNoInterfaceClassImplementingValidationItem extends BpmnElementValidationItem
{
    private final String className;

    /**
     * Constructs a new validation item for a service task with no proper interface class implementation.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the class name that does not implement the required interface
     */
    public BpmnServiceTaskNoInterfaceClassImplementingValidationItem(String elementId, File bpmnFile, String processId, String className)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Service task has no proper interface class implementation: " + className);
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
    public BpmnServiceTaskNoInterfaceClassImplementingValidationItem(String elementId, File bpmnFile, String processId, String className, String description)
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