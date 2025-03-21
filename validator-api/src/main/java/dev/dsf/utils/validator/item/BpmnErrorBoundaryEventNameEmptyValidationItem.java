package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the error boundary event's name is empty.
 * Corresponds to {@link ValidationType#BPMN_ERROR_BOUNDARY_EVENT_NAME_EMPTY}.
 */
public class BpmnErrorBoundaryEventNameEmptyValidationItem extends BpmnElementValidationItem
{

    /**
     * Constructs a new validation item for an Error Boundary Event with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnErrorBoundaryEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId, "Error Boundary Event name is empty");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnErrorBoundaryEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId, description);
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
