package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import com.fasterxml.jackson.annotation.JsonGetter;

import java.io.File;

/**
 * Abstract base class for plugin-related Lint Items.
 * Contains common fields and functionality for all plugin validation scenarios.
 */
public abstract class PluginLintItem extends AbstractLintItem {
    private final File file;
    private final String location;
    private final String message;

    public PluginLintItem(LinterSeverity severity, File file, String location, String message) {
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
        if (file == null) {
            return "unknown";
        }

        String absolutePath = file.getAbsolutePath();

        // Remove dsf-validator prefix from temp directories
        absolutePath = absolutePath.replace("dsf-validator-", "");

        // Look for common project root indicators and extract from there
        String[] rootIndicators = {
                "target" + File.separator + "classes",
                "build" + File.separator + "resources" + File.separator + "main",
                "build" + File.separator + "classes" + File.separator + "java" + File.separator + "main"
        };

        for (String indicator : rootIndicators) {
            int index = absolutePath.indexOf(indicator);
            if (index != -1) {
                // Extract from the root indicator onwards
                String relativePath = absolutePath.substring(index);
                // Normalize separators to forward slashes
                return relativePath.replace(File.separator, "/");
            }
        }

        // Fallback: return just the file name
        return file.getName();
    }

    public String getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Returns a formatted string representation of this Lint Item.
     * Format: [SEVERITY] Location: <location> - <message> (File: <filename>)
     *
     * @return formatted string representation for console output
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(getSeverity()).append("] ");

        if (location != null && !location.isBlank()) {
            sb.append("Location: ").append(location).append(" - ");
        }

        sb.append(message);

        if (file != null) {
            sb.append(" (File: ").append(getFileName()).append(")");
        }

        return sb.toString();
    }
}