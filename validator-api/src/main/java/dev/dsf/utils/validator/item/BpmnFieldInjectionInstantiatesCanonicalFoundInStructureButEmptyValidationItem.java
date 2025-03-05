package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the instantiatesCanonical field injection is found in a StructureDefinition but is empty.
 * Corresponds to {@link ValidationType#BPMN_FIELD_INJECTION_INSTANTIATES_CANONICAL_FOUND_IN_STRUCTURE_BUT_EMPTY}.
 */
public class BpmnFieldInjectionInstantiatesCanonicalFoundInStructureButEmptyValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for an instantiatesCanonical field injection that is found in a StructureDefinition but is empty.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnFieldInjectionInstantiatesCanonicalFoundInStructureButEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = "instantiatesCanonical field injection found in StructureDefinition but is empty";
    }

    /**
     * Constructs a new validation item for an instantiatesCanonical field injection that is found in a StructureDefinition but is empty, with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnFieldInjectionInstantiatesCanonicalFoundInStructureButEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }
}
