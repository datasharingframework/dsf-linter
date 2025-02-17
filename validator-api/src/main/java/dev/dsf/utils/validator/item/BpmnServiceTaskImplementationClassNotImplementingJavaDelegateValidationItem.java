package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the implementation class exists but does NOT implement JavaDelegate.
 * Corresponds to {@link ValidationType#BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_IMPLEMENTING_JAVA_DELEGATE}.
 */
public class BpmnServiceTaskImplementationClassNotImplementingJavaDelegateValidationItem extends BpmnElementValidationItem
{
    private final String className;

    /**
     * Constructs a new validation item when a Service Task implementation class
     * does not implement JavaDelegate.
     *
     * @param elementId the ID of the BPMN element (Service Task)
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the class name that was found but does not implement JavaDelegate
     */
    public BpmnServiceTaskImplementationClassNotImplementingJavaDelegateValidationItem(
            String elementId, File bpmnFile, String processId, String className)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.className = className;
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
