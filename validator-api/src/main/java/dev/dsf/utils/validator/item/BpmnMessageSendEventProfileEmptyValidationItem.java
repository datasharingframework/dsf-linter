package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Indicates that the 'profile' field is empty in a BPMN message-send scenario.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_PROFILE_EMPTY}.
 */
public class BpmnMessageSendEventProfileEmptyValidationItem extends BpmnElementValidationItem
{
    public BpmnMessageSendEventProfileEmptyValidationItem(
            String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
    }
}
