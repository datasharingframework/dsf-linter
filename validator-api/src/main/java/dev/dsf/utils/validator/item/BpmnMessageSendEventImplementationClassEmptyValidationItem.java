package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;


/**
 * Validation item indicating that a Message Send Event (throw/end) has no implementation class set.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_EMPTY}.
 */
public class BpmnMessageSendEventImplementationClassEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for an empty implementation class in a Message Send Event.
     *
     * @param elementId the ID of the BPMN element
     * @param bpmnFile  the BPMN file
     * @param processId the process definition ID
     */
    public BpmnMessageSendEventImplementationClassEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
    }


}
