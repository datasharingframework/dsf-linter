package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the message boundary event name is empty.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_BOUNDARY_EVENT_NAME_EMPTY}.
 */
public class BpmnMessageBoundaryEventNameEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for a message boundary event with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageBoundaryEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Message boundary event name is empty");
    }

    /**
     * Constructs a new validation item for a message boundary event with an empty name using a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnMessageBoundaryEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
