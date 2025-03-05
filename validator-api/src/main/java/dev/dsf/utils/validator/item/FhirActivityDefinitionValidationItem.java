package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

/**
 * <p>
 * Represents a BPMN validation issue specifically related to FHIR ActivityDefinition.
 * This class extends {@link BpmnElementValidationItem} so that it can be
 * stored in a {@code List<BpmnElementValidationItem>} while carrying FHIR-specific info.
 * </p>
 */
public class FhirActivityDefinitionValidationItem extends BpmnElementValidationItem
{
    private final String activityDefinitionUrl;
    private final String details;

    public FhirActivityDefinitionValidationItem(
            ValidationSeverity severity,
            String elementId,
            File bpmnFile,
            String processId,
            String activityDefinitionUrl,
            String details)
    {
        super(severity, elementId, bpmnFile, processId);
        this.activityDefinitionUrl = activityDefinitionUrl;
        this.details = details;
    }

    public String getActivityDefinitionUrl()
    {
        return activityDefinitionUrl;
    }

    public String getDetails()
    {
        return details;
    }

    @Override
    public String getDescription()
    {
        return String.format(
                "ActivityDefinition issue for '%s': %s",
                activityDefinitionUrl,
                details
        );
    }
}