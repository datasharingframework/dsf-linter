package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;

import java.io.File;

/**
 * Validation item for successful plugin-related operations.
 * This is used to report successful ProcessPluginDefinition service registration detection.
 */
public class PluginDefinitionValidationItemSuccess extends PluginValidationItem {

    public PluginDefinitionValidationItemSuccess(File file, String location, String message) {
        super(ValidationSeverity.SUCCESS, file, location, message);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (file=%s, location=%s, message=%s)",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFileName(),
                getLocation(),
                getMessage());
    }
}