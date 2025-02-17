package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

public class BpmnProcessIdInvalid extends BpmnValidationItem {
    public BpmnProcessIdInvalid(ValidationSeverity severity, File file) {
        super(severity, file);
    }
}
