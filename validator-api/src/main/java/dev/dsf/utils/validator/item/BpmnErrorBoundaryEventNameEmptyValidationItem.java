package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import java.io.File;

import dev.dsf.utils.validator.ValidationType;

/**
 * Validation item indicating that an Error Boundary Event has an empty name.
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
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
    }
}