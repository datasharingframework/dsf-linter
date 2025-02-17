package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation Item: Service Task Implementation Class Not Found
 * ValidationType: BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_FOUND
 * Corresponds to {@link ValidationType#BPMN_SERVICE_TASK_IMPLEMENTATION_CLASS_NOT_FOUND}.
 */
public class BpmnServiceTaskImplementationClassNotFoundValidationItem extends BpmnElementValidationItem
{
    private final String className;

    public BpmnServiceTaskImplementationClassNotFoundValidationItem(
            String elementId,
            File bpmnFile,
            String processId,
            String className)
    {
        super(ValidationSeverity.ERROR, elementId, bpmnFile, processId);
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
