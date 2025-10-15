package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import java.io.File;

/**
 * Validation item indicating that a FHIR resource file, which was referenced,
 * could not be found.
 */
public class PluginDefinitionFhirFileReferencedButNotFoundValidationItem extends PluginValidationItem
{
    /**
     * Constructs a new validation item for a referenced but missing FHIR file.
     *
     * @param pluginName   The name of the plugin referencing the file.
     * @param severity     The validation severity (usually ERROR).
     * @param resourceFile The path or name of the FHIR file that was not found.
     * @param message      A custom message describing the issue.
     */
    public PluginDefinitionFhirFileReferencedButNotFoundValidationItem(String pluginName, ValidationSeverity severity,
                                                                       File resourceFile, String message)
    {
        super(severity, resourceFile, pluginName, message);
    }

    @Override
    public String toString()
    {
        return String.format("[%s] %s (file=%s, pluginName=%s, message=Referenced FHIR file not found)",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFileName(),
                getLocation());
    }
}