package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

/**
 * A validation item that indicates a BPMN file referenced in the plugin definition
 * could not be parsed. This is a critical error that prevents further validation of the file.
 *
 * This class inherits from {@link PluginValidationItem} to provide context about the
 * plugin where the error occurred.
 */
public class PluginDefinitionUnparsableBpmnResourceValidationItem extends PluginValidationItem {

    /**
     * Constructs a new validation item for an unparsable BPMN file.
     *
     * @param file    The file that could not be parsed.
     * @param location The location or plugin name for context.
     * @param message The specific error message.
     */
    public PluginDefinitionUnparsableBpmnResourceValidationItem(File file, String location, String message) {
        super(ValidationSeverity.ERROR, file, location, message);
    }

    /**
     * Returns a formatted string representation of this validation item.
     * The format is: [SEVERITY] Location: <location> - <message> (File: <filename>)
     *
     * @return A formatted string representation for console output.
     */
    @Override
    public String toString() {
        return String.format("[%s] %s (location=%s, file=%s) : %s",
                getSeverity(),
                this.getClass().getSimpleName(),
                getLocation(),
                getFileName(),
                getMessage());
    }
}
