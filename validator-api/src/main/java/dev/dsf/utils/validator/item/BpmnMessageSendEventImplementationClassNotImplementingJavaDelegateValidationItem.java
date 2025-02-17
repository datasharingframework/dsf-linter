package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a Message Send Event's implementation class was found
 * but does not implement JavaDelegate.
 * You may need a dedicated {@link ValidationType} for this scenario.
 */
public class BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateValidationItem extends BpmnElementValidationItem
{
    private final String className;

    /**
     * Constructs a validation item where a Message Send Event's class does not implement JavaDelegate.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file
     * @param processId the process definition ID or key
     * @param className the class that does not implement JavaDelegate
     */
    public BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateValidationItem(
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
