package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the instantiatesCanonical field injection is not found in any StructureDefinition.
 * Corresponds to {@link ValidationType#BPMN_FIELD_INJECTION_INSTANTIATES_CANONICAL_NOT_PRESENT_IN_STRUCTURE_DEFINITION}.
 */
public class BpmnFieldInjectionInstantiatesCanonicalNotPresentInStructureDefinitionValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for an instantiatesCanonical field injection that is not found in any StructureDefinition.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnFieldInjectionInstantiatesCanonicalNotPresentInStructureDefinitionValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = "instantiatesCanonical field injection is not found in any StructureDefinition";
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnFieldInjectionInstantiatesCanonicalNotPresentInStructureDefinitionValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = description;
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
