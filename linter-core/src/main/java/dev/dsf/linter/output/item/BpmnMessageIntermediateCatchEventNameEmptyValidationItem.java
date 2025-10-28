package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the message intermediate catch event name is empty.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_INTERMEDIATE_CATCH_EVENT_NAME_EMPTY}.
 */
public class BpmnMessageIntermediateCatchEventNameEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for a message intermediate catch event with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageIntermediateCatchEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Message intermediate catch event name is empty");
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
        super(ValidationSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
