package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Validation item for successful plugin-related operations.
 * This is used to report successful ProcessPluginDefinition service registration detection.
 */
public class PluginDefinitionLintItemSuccess extends PluginLintItem {

    public PluginDefinitionLintItemSuccess(File file, String location, String message) {
        super(LinterSeverity.SUCCESS, file, location, message);
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