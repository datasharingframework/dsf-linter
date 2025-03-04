package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that a Message Intermediate Throw Event has a message.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_INTERMEDIATE_THROW_EVENT_HAS_MESSAGE}.
 */
public class BpmnMessageIntermediateThrowEventHasMessageValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for a Message Intermediate Throw Event that has a message.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnMessageIntermediateThrowEventHasMessageValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.description = "Message Intermediate Throw Event has a message";
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnMessageIntermediateThrowEventHasMessageValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.description = description;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

@Override
    public String toString()
    {
        return String.format(
                "[%s] %s (elementId=%s, processId=%s, file=%s) -> Intermediate Throw Event contains a <messageEventDefinition> (\"Message\" is present).",
                getSeverity(),
                this.getClass().getSimpleName(),
                getElementId(),
                getProcessId(),
                (bpmnFile != null ? bpmnFile.getName() : "N/A")
        );
    }
}
