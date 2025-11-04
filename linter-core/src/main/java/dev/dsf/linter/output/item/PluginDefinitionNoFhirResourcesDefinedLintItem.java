package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Lint Item indicating a warning for a plugin-related issue.
 */
public class PluginDefinitionNoFhirResourcesDefinedLintItem extends PluginLintItem {
    public PluginDefinitionNoFhirResourcesDefinedLintItem(File file, String location, String message) {
        super(LinterSeverity.WARN, file, location, message);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (file=%s, location=%s, message=%s)",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFile(),
                getLocation(),
                getMessage());
    }
}