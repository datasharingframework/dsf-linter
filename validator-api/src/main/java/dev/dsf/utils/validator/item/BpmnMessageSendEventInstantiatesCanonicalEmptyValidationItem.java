package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Indicates that the 'instantiatesCanonical' field is empty in a BPMN message-send scenario.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_INSTANTIATES_CANONICAL_EMPTY}.
 */
public class BpmnMessageSendEventInstantiatesCanonicalEmptyValidationItem extends BpmnElementValidationItem
{
    public BpmnMessageSendEventInstantiatesCanonicalEmptyValidationItem(
            String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
    }
}
