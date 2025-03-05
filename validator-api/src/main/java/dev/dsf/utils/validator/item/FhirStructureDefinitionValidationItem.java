package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

/**
 * <p>
 * Represents a validation issue specifically related to a StructureDefinition resource.
 * Typically used when a StructureDefinition cannot be found, or does not match expected criteria.
 * </p>
 */
public class FhirStructureDefinitionValidationItem extends BpmnElementValidationItem
{
    private final String structureDefinitionUrl;
    private final String details;

    public FhirStructureDefinitionValidationItem(
            ValidationSeverity severity,
            String elementId,
            File bpmnFile,
            String processId,
            String structureDefinitionUrl,
            String details)
    {
        super(severity, elementId, bpmnFile, processId);
        this.structureDefinitionUrl = structureDefinitionUrl;
        this.details = details;
    }

    public String getActivityDefinitionUrl()
    {
        return structureDefinitionUrl;
    }

    public String getDetails()
    {
        return details;
    }

    @Override
    public String getDescription()
    {
        return String.format(
                "StructureDefinition issue for '%s': %s",
                structureDefinitionUrl,
                details
        );
    }
}