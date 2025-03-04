package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the instantiatesCanonical field injection is empty.
 * Corresponds to {@link ValidationType#BPMN_FIELD_INJECTION_INSTANTIATES_CANONICAL_EMPTY}.
 */
public class BpmnFieldInjectionInstantiatesCanonicalEmptyValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for an empty instantiatesCanonical field injection.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnFieldInjectionInstantiatesCanonicalEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = "instantiatesCanonical field injection is empty";
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnFieldInjectionInstantiatesCanonicalEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
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
