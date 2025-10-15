package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;

import java.io.File;

/**
 * Validation item for missing ProcessPluginDefinition service registration.
 * This occurs when no ProcessPluginDefinition service file is found in META-INF/services.
 */
public class PluginDefinitionMissingServiceLoaderRegistrationValidationItem extends PluginValidationItem {

    public PluginDefinitionMissingServiceLoaderRegistrationValidationItem(File file, String location, String message) {
        super(ValidationSeverity.ERROR, file, location, message);
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
