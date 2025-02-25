package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the message name in a message send event does not match the profile value.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_MESSAGE_NAME_NOT_MATCHING_PROFILE}.
 */
public class BpmnMessageSendEventMessageNameNotMatchingProfileValidationItem extends BpmnElementValidationItem
{
    private final String description;
    private final String profileValue;
    private final String messageName;

    /**
     * Constructs a new validation item for a message send event where the message name does not match the profile value.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param profileValue the profile value provided
     * @param messageName the message name that does not match the profile value
     */
    public BpmnMessageSendEventMessageNameNotMatchingProfileValidationItem(String elementId, File bpmnFile, String processId, String profileValue, String messageName)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.profileValue = profileValue;
        this.messageName = messageName;
        this.description = "Message name '" + messageName + "' does not match the profile value: " + profileValue;
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param profileValue the profile value provided
     * @param messageName the message name that does not match the profile value
     * @param description the custom validation description
     */
    public BpmnMessageSendEventMessageNameNotMatchingProfileValidationItem(String elementId, File bpmnFile, String processId, String profileValue, String messageName, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.profileValue = profileValue;
        this.messageName = messageName;
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }

    public String getProfileValue()
    {
        return profileValue;
    }

    public String getMessageName()
    {
        return messageName;
    }
}
