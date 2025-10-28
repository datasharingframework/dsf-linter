package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the service task name is empty.
 * Corresponds to {@link ValidationType#BPMN_SERVICE_TASK_NAME_EMPTY}.
 */
public class BpmnServiceTaskNameEmptyValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for a service task with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnServiceTaskNameEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Service task name is empty");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnServiceTaskNameEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
