package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

public abstract class BpmnElementValidationItem extends BpmnValidationItem{

    protected final String elementId;
    protected final String processId;
    public BpmnElementValidationItem(ValidationSeverity severity, String elementId, File bpmnFile, String processId) {
        super(severity,bpmnFile);
        this.elementId = elementId;
        this.processId = processId;
    }

    public String getElementId()
    {
        return elementId;
    }

    public String getProcessId()
    {
        return processId;
    }

    @Override
    public String toString()
    {
        return String.format(
                "[%s] %s (elementId=%s, processId=%s, file=%s)",
                getSeverity(),
                this.getClass().getSimpleName(),
                elementId,
                processId,
                (bpmnFile != null ? bpmnFile.getName() : "N/A")
        );
    }
}

