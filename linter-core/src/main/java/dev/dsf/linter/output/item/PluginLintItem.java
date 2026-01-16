package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;

/**
 * Concrete class for plugin-related Lint Items.
 * <p>
 * This is a concrete class that can be instantiated directly. The {@link LintingType}
 * serves as the unique identifier for the type of issue, replacing the need for
 * many individual subclasses.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>
 * // Instead of: new PluginDefinitionMissingServiceLoaderRegistrationLintItem(file, location, message)
 * // Use:
 * new PluginLintItem(
 *     LinterSeverity.ERROR,
 *     LintingType.PLUGIN_DEFINITION_MISSING_SERVICE_LOADER_REGISTRATION,
 *     file,
 *     location,
 *     "ServiceLoader registration missing"
 * );
 *
 * // Or with default message:
 * PluginLintItem.of(
 *     LinterSeverity.ERROR,
 *     LintingType.PLUGIN_DEFINITION_MISSING_SERVICE_LOADER_REGISTRATION,
 *     file,
 *     location
 * );
 * </pre>
 */
public class PluginLintItem extends AbstractLintItem {
    private final File file;

    @JsonProperty("location")
    private final String location;

    @JsonProperty("message")
    private final String message;

    /**
     * Constructs a PluginLintItem with all parameters.
     *
     * @param severity the lint severity
     * @param type     the lint type/category - serves as unique identifier
     * @param file     the file associated with this issue
     * @param location the location within the plugin
     * @param message  the message describing the issue
     */
    public PluginLintItem(LinterSeverity severity, LintingType type, File file, String location, String message) {
        super(severity, type);
        this.file = file;
        this.location = location;
        this.message = message;
    }

    /**
     * Factory method that uses the default message from LintingType.
     *
     * @param severity the lint severity
     * @param type     the lint type (must have a default message)
     * @param file     the file associated with this issue
     * @param location the location within the plugin
     * @return a new PluginLintItem
     */
    public static PluginLintItem of(LinterSeverity severity,
                                    LintingType type,
                                    File file,
                                    String location) {
        return new PluginLintItem(severity, type, file, location,
                type.getDefaultMessageOrElse("Plugin issue"));
    }

    /**
     * Factory method for SUCCESS items.
     *
     * @param file     the file associated with this success
     * @param location the location within the plugin
     * @param message  the success message
     * @return a new PluginLintItem with SUCCESS severity
     */
    public static PluginLintItem success(File file, String location, String message) {
        return new PluginLintItem(LinterSeverity.SUCCESS, LintingType.SUCCESS, file, location, message);
    }

    /**
     * Backward compatible constructor (deprecated).
     *
     * @deprecated Use constructor with LintingType instead
     */
    @Deprecated
    public PluginLintItem(LinterSeverity severity, File file, String location, String message) {
        this(severity, LintingType.UNKNOWN, file, location, message);
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

        // Remove dsf-linter prefix from temp directories
        absolutePath = absolutePath.replace("dsf-linter-", "");

        // Look for common project root indicators and extract from there
        String[] rootIndicators = {
                "target" + File.separator + "classes",
                "build" + File.separator + "resources" + File.separator + "main",
                "build" + File.separator + "classes" + File.separator + "java" + File.separator + "main"
        };

        for (String indicator : rootIndicators) {
            int index = absolutePath.indexOf(indicator);
            if (index != -1) {
                String relativePath = absolutePath.substring(index);
                return relativePath.replace(File.separator, "/");
            }
        }

        return file.getName();
    }

    public String getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String getDescription() {
        return message;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(getSeverity()).append("] ");
        sb.append(getType()).append(" ");

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
