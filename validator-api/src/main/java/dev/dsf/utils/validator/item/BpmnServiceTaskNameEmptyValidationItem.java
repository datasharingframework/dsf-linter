package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a Service Task name is empty.
 * Corresponds to {@link ValidationType#BPMN_SERVICE_TASK_NAME_EMPTY}.
 */
public class BpmnServiceTaskNameEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for a Service Task with an empty name.
     *
     * @param elementId the ID of the BPMN element (Service Task)
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnServiceTaskNameEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
    }

}
