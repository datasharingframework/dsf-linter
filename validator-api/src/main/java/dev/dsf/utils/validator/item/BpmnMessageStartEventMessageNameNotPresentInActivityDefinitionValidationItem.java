package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import java.io.File;

import dev.dsf.utils.validator.ValidationType;

/**
 * Indicates that the messageName in a Message Start Event is not present
 * in any known ActivityDefinition.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_NOT_PRESENT_IN_ACTIVITY_DEFINITION}.
 */
public class BpmnMessageStartEventMessageNameNotPresentInActivityDefinitionValidationItem
        extends BpmnElementValidationItem
{
    private final String messageName;

    /**
     * Constructs a new validation item indicating that a messageName is not present
     * in any known ActivityDefinition.
     *
     * @param elementId   the BPMN element ID (Start Event)
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition or key, if available
     * @param messageName the missing message name
     */
    public BpmnMessageStartEventMessageNameNotPresentInActivityDefinitionValidationItem(
            String elementId,
            File bpmnFile,
            String processId,
            String messageName)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.messageName = messageName;
    }

    /**
     * @return the message name that was not found in ActivityDefinition
     */
    public String getMessageName()
    {
        return messageName;
    }
}
