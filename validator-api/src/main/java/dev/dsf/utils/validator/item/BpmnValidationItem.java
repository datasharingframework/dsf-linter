package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

// BPMN Validation Items
public abstract class BpmnValidationItem extends AbstractValidationItem {

    protected final File bpmnFile;
    public BpmnValidationItem(ValidationSeverity severity, File bpmnFile) {
        super(severity);
        this.bpmnFile = bpmnFile;
    }

    public File getBpmnFile()
    {
        return bpmnFile;
    }
}
