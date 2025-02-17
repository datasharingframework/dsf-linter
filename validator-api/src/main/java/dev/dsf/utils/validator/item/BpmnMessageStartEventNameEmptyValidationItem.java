package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that a Message Start Event has an empty name.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY} in some cases,
 * or you can define a dedicated type for "Start Event Name Empty."
 */
public class BpmnMessageStartEventNameEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for an empty Message Start Event name.
     *
     * @param elementId the ID of the BPMN element (Start Event)
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageStartEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        // Possibly a WARNING or ERROR
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
    }


}
