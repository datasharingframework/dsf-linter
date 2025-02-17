package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Indicates that 'instantiatesCanonical' is missing a version placeholder (e.g. "#{version}").
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_INSTANTIATES_CANONICAL_NO_VERSION_PLACEHOLDER}.
 */
public class BpmnMessageSendEventInstantiatesCanonicalNoVersionPlaceholderValidationItem
        extends BpmnElementValidationItem
{
    private final String rawValue;

    public BpmnMessageSendEventInstantiatesCanonicalNoVersionPlaceholderValidationItem(
            String elementId, File bpmnFile, String processId, String rawValue)
    {
        // Often a WARN, but can be ERROR if required
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.rawValue = rawValue;
    }

    public String getRawValue()
    {
        return rawValue;
    }
}
