package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

/**
 * Validation item indicating a warning for a plugin-related issue.
 */
public class NoFhirResourcesDefinedValidationItem extends PluginValidationItem {
    public NoFhirResourcesDefinedValidationItem(File file, String location, String message) {
        super(ValidationSeverity.WARN, file, location, message);
    }
}