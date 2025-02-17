package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Indicates that the 'messageName' is not found in the activity definitions.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_NOT_PRESENT_IN_ACTIVITY_DEFINITION}.
 */
public class BpmnMessageSendEventMessageNameNotPresentInActivityDefinitionValidationItem
        extends BpmnElementValidationItem
{
    private final String messageName;

    public BpmnMessageSendEventMessageNameNotPresentInActivityDefinitionValidationItem(
            String elementId, File bpmnFile, String processId, String messageName)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.messageName = messageName;
    }

    public String getMessageName()
    {
        return messageName;
    }
}
