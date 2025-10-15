package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import java.io.File;

import dev.dsf.linter.ValidationType;

/**
 * Validation item indicating that a field injection is not provided as a string literal.
 * Corresponds to {@link ValidationType#BPMN_FIELD_INJECTION_NOT_STRING_LITERAL}.
 */
public class BpmnFieldInjectionNotStringLiteralValidationItem extends BpmnElementValidationItem
{
    private final String fieldName;

    /**
     * Constructs a new validation item indicating that a field injection is not a string literal.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param fieldName the name of the field injection
     */
    public BpmnFieldInjectionNotStringLiteralValidationItem(String elementId, File bpmnFile, String processId, String fieldName, String description)
    {
        super(ValidationSeverity.INFO, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
        this.fieldName = fieldName;
    }

    /**
     * Returns the field name for which the literal check failed.
     *
     * @return the field name
     */
    public String getFieldName()
    {
        return fieldName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString()
    {
        return String.format("%s, fieldName=%s", super.toString(), fieldName);
    }
}