package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Indicates that 'instantiatesCanonical' does not match the given profile value.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_INSTANTIATES_CANONICAL_NOT_MATCHING_PROFILE}.
 */
public class BpmnMessageSendEventInstantiatesCanonicalNotMatchingProfileValidationItem
        extends BpmnElementValidationItem
{
    private final String profileValue;
    private final String rawValue;

    public BpmnMessageSendEventInstantiatesCanonicalNotMatchingProfileValidationItem(
            String elementId, File bpmnFile, String processId,
            String profileValue, String rawValue)
    {

        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.profileValue = profileValue;
        this.rawValue = rawValue;
    }

    public String getProfileValue()
    {
        return profileValue;
    }

    public String getRawValue()
    {
        return rawValue;
    }
}
