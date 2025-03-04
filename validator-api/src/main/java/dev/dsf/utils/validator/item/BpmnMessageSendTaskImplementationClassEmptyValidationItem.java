package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the implementation class for a message send task is empty.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_TASK_IMPLEMENTATION_CLASS_EMPTY}.
 */
public class BpmnMessageSendTaskImplementationClassEmptyValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for a message send task with an empty implementation class.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageSendTaskImplementationClassEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = "Message send task implementation class is empty";
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnMessageSendTaskImplementationClassEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = description;
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
