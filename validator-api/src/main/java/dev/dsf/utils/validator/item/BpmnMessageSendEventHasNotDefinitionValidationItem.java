package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

/**
 * Validation item indicating that a MessageEventDefinition is present in a Message Send Event,
 */
public class BpmnMessageSendEventHasNotDefinitionValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for a Message Send Event that has a MessageEventDefinition.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageSendEventHasNotDefinitionValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
    }

}
