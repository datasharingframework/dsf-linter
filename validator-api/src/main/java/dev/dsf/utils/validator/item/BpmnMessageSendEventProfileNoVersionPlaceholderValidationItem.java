package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Indicates that 'profile' is missing a version placeholder (e.g. "#{version}").
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_PROFILE_NO_VERSION_PLACEHOLDER}.
 */
public class BpmnMessageSendEventProfileNoVersionPlaceholderValidationItem extends BpmnElementValidationItem
{
    private final String rawValue;

    public BpmnMessageSendEventProfileNoVersionPlaceholderValidationItem(
            String elementId, File bpmnFile, String processId, String rawValue)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.rawValue = rawValue;
    }

    public String getRawValue()
    {
        return rawValue;
    }
}
