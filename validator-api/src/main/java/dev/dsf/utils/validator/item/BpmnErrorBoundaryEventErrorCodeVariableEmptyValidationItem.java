package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that an Error Boundary Event is missing the error code variable.
 * Corresponds to {@link ValidationType#BPMN_ERROR_BOUNDARY_EVENT_ERROR_CODE_VARIABLE_EMPTY}.
 */
public class BpmnErrorBoundaryEventErrorCodeVariableEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for an Error Boundary Event missing an error code variable.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnErrorBoundaryEventErrorCodeVariableEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
    }
}