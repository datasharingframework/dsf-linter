package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * A generic validation item typically used when no specific validation type
 * exists. For example, "floating" or ambiguous BPMN elements, or a generic warning.
 */
public class BpmnFloatingElementValidationItem extends BpmnElementValidationItem
{
    private final String description;
    private final ValidationType validationTypeOverride;
    //private final ValidationSeverity severityOverride;

    /**
     * Constructs a generic floating-element validation item with a custom message,
     * a known ValidationType, and default severity of ERROR (can be overridden).
     *
     * @param elementId            the BPMN element ID
     * @param bpmnFile             the BPMN file being validated
     * @param processId            the process definition ID or key
     * @param description          a descriptive message about the floating/ambiguous element
     * @param validationTypeOverride the validation type to assign
     */
    public BpmnFloatingElementValidationItem(
            String elementId,
            File bpmnFile,
            String processId,
            String description,
            ValidationType validationTypeOverride)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.description = description;
        this.validationTypeOverride = validationTypeOverride;
        //this.severityOverride = null; // no specific override
    }

    /**
     * Overloaded constructor that also allows specifying a custom severity.
     */
    public BpmnFloatingElementValidationItem(
            String elementId,
            File bpmnFile,
            String processId,
            String description,
            ValidationType validationTypeOverride,
            ValidationSeverity severityOverride)
    {
        super(severityOverride, elementId, bpmnFile, processId);
        this.description = description;
        this.validationTypeOverride = validationTypeOverride;
        //this.severityOverride = severityOverride;
    }

    /**
     * @return the human-readable description or reason for this floating-element validation
     */
    public String getDescription()
    {
        return description;
    }

    /*@Override
    public ValidationSeverity getSeverity()
    {
        return severityOverride != null ? severityOverride : super.getSeverity();
    }*/
}
