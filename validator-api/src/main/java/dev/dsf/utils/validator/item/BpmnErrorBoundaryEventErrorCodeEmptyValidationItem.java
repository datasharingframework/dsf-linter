package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that an Error Boundary Event's error element has an empty error code.
 * Corresponds to {@link ValidationType#BPMN_ERROR_BOUNDARY_EVENT_ERROR_CODE_EMPTY}.
 */
public class BpmnErrorBoundaryEventErrorCodeEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for an Error Boundary Event with an empty error code.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnErrorBoundaryEventErrorCodeEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
    }
}