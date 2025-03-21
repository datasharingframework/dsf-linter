package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import java.io.File;

/**
 * Validation Item: Unknown Field Injection
 * ValidationType: BPMN_UNKNOWN_FIELD_INJECTION
 *
 * <p>This validation item is used to indicate that an unknown field injection has been encountered.
 * Only the fields "profile", "messageName", and "instantiatesCanonical" are allowed.
 * Any field with a different name results in this validation error.</p>
 * Corresponds to {@link dev.dsf.utils.validator.ValidationType#BPMN_UNKNOWN_FIELD_INJECTION}.
 */
public class BpmnUnknownFieldInjectionValidationItem extends BpmnElementValidationItem
{
    private final String fieldName;

    /**
     * Constructs a new validation item for an unknown field injection with a default description.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param fieldName the name of the unknown field
     */
    public BpmnUnknownFieldInjectionValidationItem(String elementId, File bpmnFile, String processId, String fieldName)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId,
                "Unknown field injection encountered: " + fieldName);
        this.fieldName = fieldName;
    }

    /**
     * Constructs a new validation item for an unknown field injection with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param fieldName   the name of the unknown field
     * @param description the custom validation description
     */
    public BpmnUnknownFieldInjectionValidationItem(String elementId, File bpmnFile, String processId, String fieldName, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId, description);
        this.fieldName = fieldName;
    }

    public String getFieldName()
    {
        return fieldName;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    @Override
    public String toString()
    {
        return String.format("%s, unknownField=%s", super.toString(), fieldName);
    }
}
