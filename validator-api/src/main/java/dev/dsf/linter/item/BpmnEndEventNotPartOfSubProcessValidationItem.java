package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;
import java.io.File;

/**
 * Validation item indicating that an end event is not part of a subprocess.
 * Corresponds to {@link ValidationType#BPMN_END_EVENT_NOT_PART_OF_SUB_PROCESS}.
 */
public class BpmnEndEventNotPartOfSubProcessValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item for an end event that is not part of a subprocess.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnEndEventNotPartOfSubProcessValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "End event is not part of a subprocess and name is empty");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnEndEventNotPartOfSubProcessValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
