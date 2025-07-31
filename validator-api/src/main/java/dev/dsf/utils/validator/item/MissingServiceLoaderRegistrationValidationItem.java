package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

/**
 * Validation item for missing ProcessPluginDefinition service registration.
 * This occurs when no ProcessPluginDefinition service file is found in META-INF/services.
 */
public class MissingServiceLoaderRegistrationValidationItem extends PluginValidationItem {

    public MissingServiceLoaderRegistrationValidationItem(File file, String location, String message) {
        super(ValidationSeverity.ERROR, file, location, message);
    }
}
