package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that a start event is not part of a subprocess.
 * Corresponds to {@link ValidationType#BPMN_START_EVENT_NOT_PART_OF_SUB_PROCESS}.
 */
public class BpmnStartEventNotPartOfSubProcessValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for a start event that is not part of a subprocess.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnStartEventNotPartOfSubProcessValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.description = "Start event is not part of a subprocess";
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnStartEventNotPartOfSubProcessValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }
}
