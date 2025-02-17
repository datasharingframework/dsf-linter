package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_SEND_TASK_IMPLEMENTATION_CLASS_EMPTY}.
 */
public class BpmnMessageSendTaskImplementationClassEmptyValidationItem extends BpmnElementValidationItem{
    public BpmnMessageSendTaskImplementationClassEmptyValidationItem(String elementId, File file, String processId) {
        super(ValidationSeverity.ERROR, elementId, file, processId);
    }
}
