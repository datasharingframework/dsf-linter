package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Indicates that the 'messageName' does not match the given 'profile' value.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_NOT_MATCHING_PROFILE}.
 */
public class BpmnMessageSendEventMessageNameNotMatchingProfileValidationItem
        extends BpmnElementValidationItem
{
    private final String profileValue;
    private final String messageName;

    public BpmnMessageSendEventMessageNameNotMatchingProfileValidationItem(
            String elementId,
            File bpmnFile,
            String processId,
            String profileValue,
            String messageName)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.profileValue = profileValue;
        this.messageName = messageName;
    }

    public String getProfileValue()
    {
        return profileValue;
    }

    public String getMessageName()
    {
        return messageName;
    }
}
