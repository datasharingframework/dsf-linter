package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_TASK_IMPLEMENTATION_CLASS_NOT_FOUND}.
 */
public class BpmnMessageSendTaskImplementationClassNotFound extends BpmnElementValidationItem{
    private final String className;

    public BpmnMessageSendTaskImplementationClassNotFound(String elementId, File file, String processId, String className) {
        super(ValidationSeverity.ERROR, elementId, file, processId);
        this.className = className;
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
