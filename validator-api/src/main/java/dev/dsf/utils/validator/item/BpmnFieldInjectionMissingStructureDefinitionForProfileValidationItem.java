package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the profile in a message send event is missing a corresponding StructureDefinition.
 * Corresponds to {@link ValidationType#MISSING_STRUCTURE_DEFINITION_FOR_PROFILE}.
 */
public class BpmnFieldInjectionMissingStructureDefinitionForProfileValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for a message send event where the profile does not have a corresponding StructureDefinition.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnFieldInjectionMissingStructureDefinitionForProfileValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.description = "Missing StructureDefinition for profile";
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnFieldInjectionMissingStructureDefinitionForProfileValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.description = description;
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
