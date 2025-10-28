package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * <p>
 * Represents a validation issue specifically related to a StructureDefinition resource.
 * Typically used when a StructureDefinition cannot be found, or does not match expected criteria.
 * </p>
 */
public class BpmnNoStructureDefinitionFoundForMessageLintItem extends BpmnElementLintItem {
    private final String structureDefinitionUrl;
    private final String details;

    public BpmnNoStructureDefinitionFoundForMessageLintItem(
            LinterSeverity severity,
            String elementId,
            File bpmnFile,
            String processId,
            String structureDefinitionUrl,
            String details) {
        super(severity, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId,
                String.format("StructureDefinition issue for '%s': %s", structureDefinitionUrl, details));
        this.structureDefinitionUrl = structureDefinitionUrl;
        this.details = details;
    }

    public String getStructureDefinitionUrl() {
        return structureDefinitionUrl;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String getDescription() {
        return String.format(
                "StructureDefinition issue for '%s': %s",
                structureDefinitionUrl,
                details
        );
    }
}