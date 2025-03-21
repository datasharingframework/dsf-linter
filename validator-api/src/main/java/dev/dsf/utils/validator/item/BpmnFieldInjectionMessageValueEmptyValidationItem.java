package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the message value in a field injection is empty.
 * Corresponds to {@link ValidationType#BPMN_FIELD_INJECTION_MESSAGE_VALUE_EMPTY}.
 */
public class BpmnFieldInjectionMessageValueEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for an empty message value in a field injection.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnFieldInjectionMessageValueEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId, "Message value in field injection is empty");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnFieldInjectionMessageValueEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId, description);
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
