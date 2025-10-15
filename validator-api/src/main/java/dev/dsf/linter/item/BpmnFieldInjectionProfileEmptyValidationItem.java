package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the profile field injection is empty.
 * Corresponds to {@link ValidationType#BPMN_FIELD_INJECTION_PROFILE_EMPTY}.
 */
public class BpmnFieldInjectionProfileEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for an empty profile field injection.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnFieldInjectionProfileEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Profile field injection is empty");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnFieldInjectionProfileEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
