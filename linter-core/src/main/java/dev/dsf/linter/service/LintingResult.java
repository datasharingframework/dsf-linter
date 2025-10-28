package dev.dsf.linter.service;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.AbstractLintItem;

import java.util.List;

/**
 * Data class containing linting results.
 * <p>
 * This class holds a list of lint items and provides convenience methods
 * to access counts of items by severity (ERROR, WARN, SUCCESS) and to
 * filter them.
 * </p>
 */
public class LintingResult {

    private final List<AbstractLintItem> items;
    private final int errorCount;
    private final int warningCount;
    private final int successCount;

    /**
     * Constructs a new LintingResult.
     *
     * @param items the list of lint items from a linting process
     */
    public LintingResult(List<AbstractLintItem> items) {
        this.items = items;

        this.errorCount = (int) items.stream()
                .filter(item -> item.getSeverity() == LinterSeverity.ERROR)
                .count();

        this.warningCount = (int) items.stream()
                .filter(item -> item.getSeverity() == LinterSeverity.WARN)
                .count();

        this.successCount = (int) items.stream()
                .filter(item -> item.getSeverity() == LinterSeverity.SUCCESS)
                .count();
    }

    public List<AbstractLintItem> getItems() {
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

    public List<AbstractLintItem> getSuccessItems() {
        return items.stream()
                .filter(item -> item.getSeverity() == LinterSeverity.SUCCESS)
                .toList();
    }

    public List<AbstractLintItem> getNonSuccessItems() {
        return items.stream()
                .filter(item -> item.getSeverity() != LinterSeverity.SUCCESS)
                .toList();
    }
}