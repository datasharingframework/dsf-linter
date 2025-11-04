package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * A Lint Item that indicates a FHIR resource file referenced in the plugin definition
 * could not be parsed. This is a critical error that prevents further linting of the file.
 * <p>
 * This class inherits from {@link PluginLintItem} to provide context about the
 * plugin where the error occurred.
 */
public class PluginDefinitionUnparsableFhirResourceLintItem extends PluginLintItem {

    /**
     * Constructs a new Lint Item for an unparsable FHIR resource file.
     *
     * @param file     The file that could not be parsed.
     * @param location The location or plugin name for context.
     * @param message  The specific error message.
     */
    public PluginDefinitionUnparsableFhirResourceLintItem(File file, String location, String message) {
        super(LinterSeverity.ERROR, file, location, message);
    }

    /**
     * Returns a formatted string representation of this Lint Item.
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