package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * A generic validation item typically used when no specific validation type
 * exists. For example, "floating" or ambiguous BPMN elements, or a generic warning.
 * Corresponds to {@link ValidationType#BPMN_FLOATING_ELEMENT}.
 */
public class BpmnFloatingElementValidationItem extends BpmnElementValidationItem
{
    private final String description;
    private final ValidationType validationTypeOverride;

    /**
     * Constructs a generic floating-element validation item with a custom message,
     * a known ValidationType, and default severity of WARN.
     *
     * @param elementId             the BPMN element ID
     * @param bpmnFile              the BPMN file being validated
     * @param processId             the process definition ID or key
     * @param description           a descriptive message about the floating/ambiguous element
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
    }

    /**
     * Overloaded constructor that also allows specifying a custom severity.
     *
     * @param elementId             the BPMN element ID
     * @param bpmnFile              the BPMN file being validated
     * @param processId             the process definition ID or key
     * @param description           a descriptive message about the floating/ambiguous element
     * @param validationTypeOverride the validation type to assign
     * @param severityOverride      the custom severity to use
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
    }

    /**
     * @return the human-readable description or reason for this floating-element validation
     */
    public String getDescription()
    {
        return description;
    }


    /**
     * Overrides the default toString() method to include the description and other relevant details.
     *
     * @return a string representation of this validation item including elementId, processId, file, description,
     *         validation type, and severity.
     */
    /*
    @Override
    public String toString()
    {
        return "BpmnFloatingElementValidationItem{" +
                "elementId='" + getElementId() + '\'' +
                ", processId='" + getProcessId() + '\'' +
                ", file=" + getBpmnFile() +
                ", description='" + description + '\'' +
                ", validationTypeOverride=" + validationTypeOverride +
                ", severity=" + getSeverity() +
                '}';
    } */
}
