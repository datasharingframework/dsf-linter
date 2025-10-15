package dev.dsf.linter.util;

import dev.dsf.linter.item.BpmnValidationItem;
import dev.dsf.linter.item.FhirValidationItem;
import dev.dsf.linter.item.PluginValidationItem;
import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.item.AbstractValidationItem;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ValidationUtils {

    /**
     * Computes the count of ERROR, WARNING, and INFO severities for a given list of validation items.
     */
    public static class SeverityCount {
        private final long errors;
        private final long warnings;
        private final long infos;
        private final long total;

        public SeverityCount(long errors, long warnings, long infos) {
            this.errors = errors;
            this.warnings = warnings;
            this.infos = infos;
            this.total = errors + warnings + infos;
        }

        public long getErrors() {
            return errors;
        }

        public long getWarnings() {
            return warnings;
        }

        public long getInfos() {
            return infos;
        }

        public long getTotal() {
            return total;
        }
    }

    /**
     * Counts severities (ERROR, WARN, INFO) in the given list of validation items.
     *
     * @param items the list of validation items
     * @return a SeverityCount object containing the counts
     */
    public static SeverityCount countSeverities(List<? extends AbstractValidationItem> items) {
        if (items == null || items.isEmpty()) {
            return new SeverityCount(0, 0, 0);
        }

        long errors = items.stream()
                .filter(i -> i.getSeverity() == ValidationSeverity.ERROR)
                .count();
        long warnings = items.stream()
                .filter(i -> i.getSeverity() == ValidationSeverity.WARN)
                .count();
        long infos = items.stream()
                .filter(i -> i.getSeverity() == ValidationSeverity.INFO)
                .count();

        return new SeverityCount(errors, warnings, infos);
    }

    /**
     * Checks if the given string is null or empty (after trimming).
     *
     * @param value the string to check
     * @return {@code true} if the string is null or empty; {@code false} otherwise
     */
    public static boolean isEmpty(String value) {
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
            List<AbstractValidationItem> items, ValidationSeverity severity) {
        if (items == null || severity == null)
            return List.of();

        return items.stream()
                .filter(item -> item.getSeverity() == severity)
                .collect(Collectors.toList());
    }

    /**
     * Filters validation items to include only BpmnValidationItem instances.
     *
     * @param items the list of validation items
     * @return a list containing only BPMN validation items
     */
    public static List<AbstractValidationItem> onlyBpmnItems(List<AbstractValidationItem> items) {
        if (items == null) return List.of();

        return items.stream()
                .filter(item -> item instanceof BpmnValidationItem)
                .collect(Collectors.toList());
    }

    /**
     * Filters validation items to include only FhirValidationItem instances.
     *
     * @param items the list of validation items
     * @return a list containing only FHIR validation items
     */
    public static List<AbstractValidationItem> onlyFhirItems(List<AbstractValidationItem> items) {
        if (items == null) return List.of();

        return items.stream()
                .filter(item -> item instanceof FhirValidationItem)
                .collect(Collectors.toList());
    }

    /**
     * Filters validation items to include only PluginValidationItem instances.
     *
     * @param items the list of validation items
     * @return a list containing only Plugin validation items
     */
    public static List<AbstractValidationItem> onlyPluginItems(List<AbstractValidationItem> items) {
        if (items == null) return List.of();

        return items.stream()
                .filter(item -> item instanceof PluginValidationItem)
                .collect(Collectors.toList());
    }
}