package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation Item: Unknown Field Injection
 * ValidationType: BPMN_UNKNOWN_FIELD_INJECTION
 *
 * <p>This validation item is used to indicate that an unknown field injection has been encountered.
 * Only the fields "profile", "messageName", and "instantiatesCanonical" are allowed.
 * Any field with a different name results in this validation error.</p>
 * Corresponds to {@link ValidationType#BPMN_UNKNOWN_FIELD_INJECTION}.
 */
public class BpmnUnknownFieldInjectionValidationItem extends BpmnElementValidationItem
{
    private final String fieldName;

    /**
     * Constructs a new BpmnUnknownFieldInjectionValidationItem.
     *
     * @param elementId the BPMN element ID where the error occurred
     * @param bpmnFile the BPMN file being validated
     * @param processId the process identifier associated with the BPMN model
     * @param fieldName the name of the unknown field injection that was encountered
     */
    public BpmnUnknownFieldInjectionValidationItem(String elementId, File bpmnFile, String processId, String fieldName)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.fieldName = fieldName;
    }

    /**
     * Returns the name of the unknown field injection.
     *
     * @return the unknown field name
     */
    public String getFieldName()
    {
        return fieldName;
    }

    @Override
    public String toString()
    {
        return String.format("%s, unknownField=%s", super.toString(), fieldName);
    }
}
