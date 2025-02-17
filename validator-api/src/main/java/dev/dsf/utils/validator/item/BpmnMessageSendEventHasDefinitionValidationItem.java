package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

import dev.dsf.utils.validator.ValidationType;

/**
 * Validation item indicating that a MessageEventDefinition is present in a Message Send Event,
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_HAS_DEFINITION}.
 */
public class BpmnMessageSendEventHasDefinitionValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for a Message Send Event that has a MessageEventDefinition.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageSendEventHasDefinitionValidationItem(String elementId, File bpmnFile, String processId)
    {
        // Possibly a WARNING or INFO
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
    }

}
