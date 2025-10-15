package dev.dsf.linter.service;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.item.AbstractValidationItem;

import java.util.List;

/**
 * Data class containing validation results.
 * <p>
 * This class holds a list of validation items and provides convenience methods
 * to access counts of items by severity (ERROR, WARN, SUCCESS) and to
 * filter them.
 * </p>
 */
public class ValidationResult {

    private final List<AbstractValidationItem> items;
    private final int errorCount;
    private final int warningCount;
    private final int successCount;

    /**
     * Constructs a new ValidationResult.
     *
     * @param items the list of validation items from a validation process
     */
    public ValidationResult(List<AbstractValidationItem> items) {
        this.items = items;

        this.errorCount = (int) items.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.ERROR)
                .count();

        this.warningCount = (int) items.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.WARN)
                .count();

        this.successCount = (int) items.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                .count();
    }

    public List<AbstractValidationItem> getItems() {
        return items;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    public boolean hasWarnings() {
        return warningCount > 0;
    }

    public List<AbstractValidationItem> getSuccessItems() {
        return items.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                .toList();
    }

    public List<AbstractValidationItem> getNonSuccessItems() {
        return items.stream()
                .filter(item -> item.getSeverity() != ValidationSeverity.SUCCESS)
                .toList();
    }
}