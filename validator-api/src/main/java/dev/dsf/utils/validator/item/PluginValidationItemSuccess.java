package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

/**
 * Validation item for successful plugin-related operations.
 * This is used to report successful ProcessPluginDefinition service registration detection.
 */
public class PluginValidationItemSuccess extends PluginValidationItem {

    public PluginValidationItemSuccess(File file, String location, String message) {
        super(ValidationSeverity.SUCCESS, file, location, message);
    }
}
