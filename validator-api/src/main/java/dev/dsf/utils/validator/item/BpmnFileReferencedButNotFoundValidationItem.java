package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

/**
 * Validation item indicating that a BPMN file, which was referenced in the class ProcessDefinition,
 * could not be found in ressource.
 */
public class BpmnFileReferencedButNotFoundValidationItem extends BpmnValidationItem
{
    private final String description;

    /**
     * Constructs a new validation item for a referenced but missing BPMN file.
     *
     * @param severity     The severity of the validation error (usually ERROR).
     * @param bpmnFile     The BPMN file that was not found.
     * @param description  A custom message describing the issue.
     */
    public BpmnFileReferencedButNotFoundValidationItem(ValidationSeverity severity, File bpmnFile, String description)
    {
        super(severity, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn");
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }

    @Override
    public String toString()
    {
        return String.format("[%s] %s (file=%s) : %s", getSeverity(), this.getClass().getSimpleName(), getBpmnFile(),
                description);
    }
}