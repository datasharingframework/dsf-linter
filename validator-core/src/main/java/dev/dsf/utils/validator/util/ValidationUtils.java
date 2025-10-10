package dev.dsf.utils.validator.util;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.item.AbstractValidationItem;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ValidationUtils {

    /**
     * Checks if the given string is null or empty (after trimming).
     *
     * @param value the string to check
     * @return {@code true} if the string is null or empty; {@code false} otherwise
     */
    public static boolean isEmpty(String value)
    {
        return (value == null || value.trim().isEmpty());
    }

    /**
     * Checks if the given string contains a version placeholder.
     * <p>
     * A valid placeholder is expected to be in the format "${someWord}" or "#{someWord}", with at least one character inside.
     * </p>
     *
     * @param rawValue the string to check for a placeholder
     * @return {@code true} if the string contains a valid placeholder; {@code false} otherwise
     */
    public static boolean containsPlaceholder(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return false;
        }
        // Regex explanation:
        // (\\$|#)      : Matches either a '$' or '#' character.
        // "\\{"        : Matches the literal '{'.
        // "[^\\}]+":   : Ensures that at least one character (that is not '}') is present.
        // "\\}"        : Matches the literal '}'.
        // ".*" before and after allows the placeholder to appear anywhere in the string.
        return rawValue.matches(".*(?:\\$|#)\\{[^\\}]+\\}.*");
    }

    public static File getFile(Path filePath) {
        Path current = filePath.getParent();
        while (current != null) {
            if (new File(current.toFile(), "pom.xml").exists()) {
                return current.toFile();
            }
            current = current.getParent();
        }
        return filePath.getParent() != null ? filePath.getParent().toFile() : new File(".");
    }

    /**
     * Filters validation items by the specified severity.
     *
     * @param items the list of validation items
     * @param severity the desired {@link ValidationSeverity}
     * @return a list containing only items with the given severity
     */
    public static List<AbstractValidationItem> filterBySeverity(
            List<AbstractValidationItem> items, ValidationSeverity severity)
    {
        if (items == null || severity == null)
            return List.of();

        return items.stream()
                .filter(item -> item.getSeverity() == severity)
                .collect(Collectors.toList());
    }

}
