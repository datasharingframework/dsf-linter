package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.FlowElementType;
import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating an issue with a BPMN Flow Element.
 */
public class BpmnFlowElementValidationItem extends BpmnElementValidationItem
{
    private final ValidationType validationTypeOverride;
    private final FlowElementType flowElementType;

    /**
     * Constructs a new validation item for a BPMN Flow Element with a default error description.
     * The default severity is set to {@link ValidationSeverity#WARN}.
     *
     * @param elementId              the BPMN element ID
     * @param bpmnFile               the BPMN file being validated
     * @param processId              the process definition ID or key
     * @param validationTypeOverride the validation type to associate with this issue
     * @param flowElementType        the specific flow element type that categorizes this issue
     */
    public BpmnFlowElementValidationItem(String elementId, File bpmnFile, String processId,
                                         ValidationType validationTypeOverride,
                                         FlowElementType flowElementType)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId, "Flow element validation WARN");
        this.validationTypeOverride = validationTypeOverride;
        this.flowElementType = flowElementType;
    }

    /**
     * Constructs a new validation item for a BPMN Flow Element with a custom description.
     *
     * @param elementId              the BPMN element ID
     * @param bpmnFile               the BPMN file being validated
     * @param processId              the process definition ID or key
     * @param description            the custom validation description
     * @param validationTypeOverride the validation type to associate with this issue
     * @param severityOverride       the severity to assign to this validation issue
     * @param flowElementType        the specific flow element type that categorizes this issue
     */
    public BpmnFlowElementValidationItem(String elementId, File bpmnFile, String processId, String description,
                                         ValidationType validationTypeOverride,
                                         ValidationSeverity severityOverride,
                                         FlowElementType flowElementType)
    {
        super(severityOverride, elementId, bpmnFile, processId, description);
        this.validationTypeOverride = validationTypeOverride;
        this.flowElementType = flowElementType;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    /**
     * Returns the validation type override for this flow element validation item.
     *
     * @return the {@link ValidationType} associated with this validation issue.
     */
    public ValidationType getValidationTypeOverride()
    {
        return validationTypeOverride;
    }

    /**
     * Returns the flow element type that categorizes this validation issue.
     *
     * @return the {@link FlowElementType} if specified, or {@code null} if not.
     */
    public FlowElementType getFlowElementType()
    {
        return flowElementType;
    }

    @Override
    public String toString()
    {
        return "BpmnFlowElementValidationItem{" +
                "elementId='" + getElementId() + '\'' +
                ", processId='" + getProcessId() + '\'' +
                ", file=" + getBpmnFile() +
                ", description='" + description + '\'' +
                ", validationTypeOverride=" + validationTypeOverride +
                ", flowElementType=" + flowElementType +
                ", severity=" + getSeverity() +
                '}';
    }
}
