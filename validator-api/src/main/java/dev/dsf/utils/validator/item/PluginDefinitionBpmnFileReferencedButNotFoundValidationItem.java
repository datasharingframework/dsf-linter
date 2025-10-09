package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import java.io.File;

/**
 * Validation item indicating that a BPMN file, which was referenced in the class ProcessDefinition,
 * could not be found in the resources.
 */
public class PluginDefinitionBpmnFileReferencedButNotFoundValidationItem extends PluginValidationItem
{
    /**
     * Constructs a new validation item for a referenced but missing BPMN file.
     *
     * @param pluginName   The name of the plugin referencing the file.
     * @param severity     The severity of the validation error (usually ERROR).
     * @param bpmnFile     The BPMN file that was not found.
     * @param description  A custom message describing the issue.
     */
    public PluginDefinitionBpmnFileReferencedButNotFoundValidationItem(String pluginName, ValidationSeverity severity, File bpmnFile, String description)
    {
        super(severity, bpmnFile, pluginName, description);
    }

    @Override
    public String toString()
    {
        return String.format("[%s] %s (file=%s, pluginName=%s, message=Referenced BPMN file not found)",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFileName(),
                getLocation());
    }
}