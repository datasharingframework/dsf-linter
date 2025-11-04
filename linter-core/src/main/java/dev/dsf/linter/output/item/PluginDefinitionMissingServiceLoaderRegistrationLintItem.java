package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Lint Item for missing ProcessPluginDefinition service registration.
 * This occurs when no ProcessPluginDefinition service file is found in META-INF/services.
 */
public class PluginDefinitionMissingServiceLoaderRegistrationLintItem extends PluginLintItem {

    public PluginDefinitionMissingServiceLoaderRegistrationLintItem(File file, String location, String message) {
        super(LinterSeverity.ERROR, file, location, message);
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
