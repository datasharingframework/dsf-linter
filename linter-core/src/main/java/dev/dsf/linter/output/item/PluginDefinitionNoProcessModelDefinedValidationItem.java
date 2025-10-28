package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;

import java.io.File;

/**
 * Validation item indicating a warning for a plugin-related issue.
 */
public class PluginDefinitionNoProcessModelDefinedValidationItem extends PluginValidationItem {
    public PluginDefinitionNoProcessModelDefinedValidationItem(File file, String location, String message) {
        super(ValidationSeverity.WARN, file, location, message);
    }

    @Override
    public String toString()
    {
        return String.format("[%s] %s (file=%s, location=%s, message=%s)",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFile(),
                getLocation(),
                getMessage());
    }
}

