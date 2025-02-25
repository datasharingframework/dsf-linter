package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the error name in an Error Boundary Event is empty.
 * Corresponds to {@link ValidationType#BPMN_ERROR_BOUNDARY_EVENT_ERROR_NAME_EMPTY}.
 */
public class BpmnErrorBoundaryEventErrorNameEmptyValidationItem extends BpmnElementValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for an Error Boundary Event with an empty error name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnErrorBoundaryEventErrorNameEmptyValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = "Error name is empty in Error Boundary Event";
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnErrorBoundaryEventErrorNameEmptyValidationItem(String elementId, File bpmnFile, String processId, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }
}
