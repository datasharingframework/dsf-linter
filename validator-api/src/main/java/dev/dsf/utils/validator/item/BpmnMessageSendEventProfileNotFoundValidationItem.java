package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Indicates that the specified profile is not recognized or not found.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_PROFILE_NOT_FOUND}.
 */
public class BpmnMessageSendEventProfileNotFoundValidationItem extends BpmnElementValidationItem
{
    private final String rawValue;

    public BpmnMessageSendEventProfileNotFoundValidationItem(
            String elementId, File bpmnFile, String processId, String rawValue)
    {
        // Example: ERROR if the profile must exist
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.rawValue = rawValue;
    }

    public String getRawValue()
    {
        return rawValue;
    }
}
