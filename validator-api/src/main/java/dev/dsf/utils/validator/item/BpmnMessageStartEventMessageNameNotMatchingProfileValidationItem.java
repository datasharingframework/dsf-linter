package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import java.io.File;

import dev.dsf.utils.validator.ValidationType;

/**
 * Indicates that a messageName in a Message Start Event does not match
 * the required profile or structure definition.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_NOT_MATCHING_PROFILE}.
 */
public class BpmnMessageStartEventMessageNameNotMatchingProfileValidationItem
        extends BpmnElementValidationItem
{
    private final String messageName;

    /**
     * Constructs a new validation item indicating that a messageName does not match
     * the required profile or structure definition.
     *
     * @param elementId   the BPMN element ID (Start Event)
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition or key, if available
     * @param messageName the name that failed to match the profile
     */
    public BpmnMessageStartEventMessageNameNotMatchingProfileValidationItem(
            String elementId,
            File bpmnFile,
            String processId,
            String messageName)
    {
        // Use ERROR, WARN, or INFO as desired; here we set ERROR arbitrarily
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.messageName = messageName;
    }

    /**
     * @return the message name that did not match the profile
     */
    public String getMessageName()
    {
        return messageName;
    }
}
