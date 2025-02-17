package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a Service Task is missing its implementation class.
 * Corresponds to {@link ValidationType#BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_EMPTY}.
 */
public class BpmnServiceTaskImplementationClassEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for a Service Task implementation-class-empty scenario.
     *
     * @param elementId the ID of the BPMN element (Service Task)
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnServiceTaskImplementationClassEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        // We assume ERROR severity for a missing implementation class
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
    }


}
