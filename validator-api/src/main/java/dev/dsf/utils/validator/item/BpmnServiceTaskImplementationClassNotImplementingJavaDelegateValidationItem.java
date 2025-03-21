package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the implementation class for a service task does not implement the JavaDelegate interface.
 * Corresponds to {@link ValidationType#BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_IMPLEMENTING_JAVA_DELEGATE}.
 */
public class BpmnServiceTaskImplementationClassNotImplementingJavaDelegateValidationItem extends BpmnElementValidationItem
{
    private final String className;

    /**
     * Constructs a new validation item for a service task where the implementation class does not implement JavaDelegate.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the implementation class name that does not implement JavaDelegate
     */
    public BpmnServiceTaskImplementationClassNotImplementingJavaDelegateValidationItem(String elementId, File bpmnFile, String processId, String className)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId, "Service task implementation class does not implement JavaDelegate: " + className);
        this.className = className;
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param className   the implementation class name that does not implement JavaDelegate
     * @param description the custom validation description
     */
    public BpmnServiceTaskImplementationClassNotImplementingJavaDelegateValidationItem(String elementId, File bpmnFile, String processId, String className, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId, description);
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
                "%s, missingClassName=%s",
                super.toString(),
                className
        );
    }

}
