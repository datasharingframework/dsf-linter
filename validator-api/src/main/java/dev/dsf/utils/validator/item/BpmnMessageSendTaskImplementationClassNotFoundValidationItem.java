package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the implementation class for a message send task was not found.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_TASK_IMPLEMENTATION_CLASS_NOT_FOUND}.
 */
public class BpmnMessageSendTaskImplementationClassNotFoundValidationItem extends BpmnElementValidationItem
{
    private final String description;
    private final String className;

    /**
     * Constructs a new validation item for a message send task where the implementation class was not found.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the implementation class name that was not found
     */
    public BpmnMessageSendTaskImplementationClassNotFoundValidationItem(String elementId, File bpmnFile, String processId, String className)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.className = className;
        this.description = "Message send task implementation class not found: " + className;
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param className   the implementation class name that was not found
     * @param description the custom validation description
     */
    public BpmnMessageSendTaskImplementationClassNotFoundValidationItem(String elementId, File bpmnFile, String processId, String className, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
        this.className = className;
        this.description = description;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    public String getClassName()
    {
        return className;
    }
}
