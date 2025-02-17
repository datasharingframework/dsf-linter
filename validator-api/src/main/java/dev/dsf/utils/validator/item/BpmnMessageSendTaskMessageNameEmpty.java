package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

public class BpmnMessageSendTaskMessageNameEmpty extends BpmnElementValidationItem{

    public BpmnMessageSendTaskMessageNameEmpty(String elementId, File file, String processId) {
        super(ValidationSeverity.ERROR, elementId, file, processId);
    }
}
