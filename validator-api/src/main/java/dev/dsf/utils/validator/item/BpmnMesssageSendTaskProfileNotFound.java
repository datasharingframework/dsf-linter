package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

public class BpmnMesssageSendTaskProfileNotFound extends BpmnElementValidationItem{

    private final String messageName;
    public BpmnMesssageSendTaskProfileNotFound(String elementId, File file, String processId, String messageName) {
        super(ValidationSeverity.ERROR, elementId, file, processId);
        this.messageName = messageName;
    }
}
