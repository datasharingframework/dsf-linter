package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the implementation for a service task does not exist.
 * Corresponds to {@link ValidationType#BPMN_SERVICE_TASK_IMPLEMENTATION_NOT_EXIST}.
 */
public class BpmnServiceTaskImplementationNotExistValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for a service task where the implementation does not exist.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnServiceTaskImplementationNotExistValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Service task implementation does not exist");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnServiceTaskImplementationNotExistValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
