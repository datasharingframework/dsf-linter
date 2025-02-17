package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the specified implementation class for a Message Send Event
 * cannot be found on the classpath.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND}.
 */
public class BpmnMessageSendEventImplementationClassNotFoundValidationItem extends BpmnElementValidationItem
{
    private final String className;

    /**
     * Constructs a new validation item for a "class not found" scenario in a Message Send Event.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the class name that was not found
     */
    public BpmnMessageSendEventImplementationClassNotFoundValidationItem(
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
