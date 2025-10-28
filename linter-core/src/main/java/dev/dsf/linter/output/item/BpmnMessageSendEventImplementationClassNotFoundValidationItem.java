package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the implementation class for a message send event was not found.
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND}.
 */
public class BpmnMessageSendEventImplementationClassNotFoundValidationItem extends BpmnElementValidationItem
{
    private final String className;

    /**
     * Constructs a new validation item for a message send event where the implementation class was not found.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the implementation class name that was not found
     */
    public BpmnMessageSendEventImplementationClassNotFoundValidationItem(String elementId, File bpmnFile, String processId, String className)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Message send event implementation class not found: " + className);
        this.className = className;
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
    public BpmnMessageSendEventImplementationClassNotFoundValidationItem(String elementId, File bpmnFile, String processId, String className, String description)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
        this.className = className;
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

    @Override
    public String toString()
    {
        return String.format(
                "%s, missingClassName=%s",
                super.toString(),
                className
        );
    }
}
