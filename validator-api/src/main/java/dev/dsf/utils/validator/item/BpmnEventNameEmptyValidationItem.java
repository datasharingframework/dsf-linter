package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the event name is empty.
 * Corresponds to {@link ValidationType#BPMN_EVENT_NAME_EMPTY}.
 */
public class BpmnEventNameEmptyValidationItem extends BpmnElementValidationItem
{

    /**
     * Constructs a new validation item for an event with an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId,"Event name is empty" );
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnEventNameEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId, description);
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    /**
     * Returns a string representation of the validation item that includes the element ID,
     * BPMN file, process ID, severity, and description.
     *
     * @return a human-readable string representation of this validation item.
     */
    @Override
    public String toString() {
        return "BpmnEventNameEmptyValidationItem{" +
                "elementId=" + getElementId() +
                ", bpmnFile=" + getBpmnFile() +
                ", processId=" + getProcessId() +
                ", severity=" + getSeverity() +
                ", description='" + description + '\'' +
                '}';
    }
}
