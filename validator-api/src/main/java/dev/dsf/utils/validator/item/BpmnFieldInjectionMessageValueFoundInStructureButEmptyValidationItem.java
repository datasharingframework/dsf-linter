package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the message value in a field injection is found in a StructureDefinition but is empty.
 * Corresponds to {@link ValidationType#BPMN_FIELD_INJECTION_MESSAGE_VALUE_FOUND_IN_STRUCTURE_BUT_EMPTY}.
 */
public class BpmnFieldInjectionMessageValueFoundInStructureButEmptyValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for a field injection where the message value is found in a StructureDefinition but is empty.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnFieldInjectionMessageValueFoundInStructureButEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = "Message value in field injection found in StructureDefinition but is empty";
    }

    /**
     * Constructs a new validation item for a field injection with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnFieldInjectionMessageValueFoundInStructureButEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }
}
