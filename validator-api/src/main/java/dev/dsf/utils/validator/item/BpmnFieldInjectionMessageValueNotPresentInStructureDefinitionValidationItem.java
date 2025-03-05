package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the message value in a field injection is not present in any StructureDefinition.
 * Corresponds to {@link ValidationType#BPMN_FIELD_INJECTION_MESSAGE_VALUE_NOT_PRESENT_IN_STRUCTURE_DEFINITION}.
 */
public class BpmnFieldInjectionMessageValueNotPresentInStructureDefinitionValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for a field injection where the message value is not present in any StructureDefinition.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnFieldInjectionMessageValueNotPresentInStructureDefinitionValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = "Message value in field injection is not present in any StructureDefinition";
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnFieldInjectionMessageValueNotPresentInStructureDefinitionValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }
}
