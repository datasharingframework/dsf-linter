package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a Message Send Event (e.g., an intermediate throw or end event)
 * has an empty name.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_NAME_EMPTY}.
 */
public class BpmnMessageSendEventNameEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for an empty Message Send Event name.
     *
     * @param elementId the ID of the BPMN element (Throw Event or End Event)
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageSendEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
    }

}
