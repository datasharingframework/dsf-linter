package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Indicates that there is no recognized ActivityDefinition yet for the given scenario.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_NO_ACTIVITY_DEFINITION_YET}.
 */
public class BpmnMessageSendEventNoActivityDefinitionYetValidationItem extends BpmnElementValidationItem
{
    private final String rawValue;

    public BpmnMessageSendEventNoActivityDefinitionYetValidationItem(
            String elementId, File bpmnFile, String processId, String rawValue)
    {
        // Possibly a WARN, since the definition might be loaded later
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.rawValue = rawValue;
    }

    public String getRawValue()
    {
        return rawValue;
    }
}
