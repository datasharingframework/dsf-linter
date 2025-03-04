package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the message name in a message send event is not present in any ActivityDefinition.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_NOT_PRESENT_IN_ACTIVITY_DEFINITION}.
 */
public class BpmnMessageSendEventMessageNameNotPresentInActivityDefinitionValidationItem extends BpmnElementValidationItem
{
    private final String description;
    private final String messageName;

    /**
     * Constructs a new validation item for a message send event where the message name is not present in any ActivityDefinition.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param messageName the message name that is not present in any ActivityDefinition
     */
    public BpmnMessageSendEventMessageNameNotPresentInActivityDefinitionValidationItem(String elementId, File bpmnFile, String processId, String messageName)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.messageName = messageName;
        this.description = "Message name '" + messageName + "' is not present in any ActivityDefinition";
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param messageName the message name that is not present in any ActivityDefinition
     * @param description the custom validation description
     */
    public BpmnMessageSendEventMessageNameNotPresentInActivityDefinitionValidationItem(String elementId, File bpmnFile, String processId, String messageName, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.messageName = messageName;
        this.description = description;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    public String getMessageName()
    {
        return messageName;
    }
}
