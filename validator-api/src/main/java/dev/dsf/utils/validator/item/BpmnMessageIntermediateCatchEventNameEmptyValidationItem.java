package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the message intermediate catch event name is empty.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_INTERMEDIATE_CATCH_EVENT_NAME_EMPTY}.
 */
public class BpmnMessageIntermediateCatchEventNameEmptyValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for a message intermediate catch event with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageIntermediateCatchEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.description = "Message intermediate catch event name is empty";
    }

    /**
     * Constructs a new validation item for a message intermediate catch event with an empty name and a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnMessageIntermediateCatchEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.description = description;
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
