package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import com.fasterxml.jackson.annotation.JsonGetter;

import java.io.File;

/**
 * Abstract base class for plugin-related validation items.
 * Contains common fields and functionality for all plugin validation scenarios.
 */
public abstract class PluginValidationItem extends AbstractValidationItem {
    private final File file;
    private final String location;
    private final String message;

    public PluginValidationItem(ValidationSeverity severity, File file, String location, String message) {
        super(severity);
        this.file = file;
        this.location = location;
        this.message = message;
    }

    public File getFile() {
        return file;
    }

    @JsonGetter("file")
    public String getFileName() {
        return file.getName();
    }

    public String getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }
}
