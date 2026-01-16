package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.ProcessingLevel;
import dev.dsf.linter.output.LinterSeverity;

/**
 * Abstract base class for all lint items, implementing the {@link LintItem} interface.
 * <p>
 * This class provides common fields and functionality shared by all lint item types.
 * Subclasses should provide domain-specific fields (e.g., BPMN element ID, FHIR reference).
 * </p>
 */
public abstract class AbstractLintItem implements LintItem {
    private final LinterSeverity severity;
    private final LintingType type;
    private final ProcessingLevel processingLevel = ProcessingLevel.FILE;

    /**
     * Constructs an AbstractLintItem with the given severity and type.
     *
     * @param severity the lint severity
     * @param type     the lint type/category
     */
    public AbstractLintItem(LinterSeverity severity, LintingType type) {
        this.severity = severity;
        this.type = type != null ? type : LintingType.UNKNOWN;
    }

    /**
     * Constructs an AbstractLintItem with severity only (for backward compatibility).
     * The type will be set to UNKNOWN.
     *
     * @param severity the lint severity
     * @deprecated Use {@link #AbstractLintItem(LinterSeverity, LintingType)} instead
     */
    @Deprecated
    public AbstractLintItem(LinterSeverity severity) {
        this(severity, LintingType.UNKNOWN);
    }

    @Override
    public LinterSeverity getSeverity() {
        return severity;
    }

    @Override
    public LintingType getType() {
        return type;
    }

    /**
     * Returns the processing level for this lint item.
     *
     * @return the processing level
     */
    public ProcessingLevel getProcessingLevel() {
        return processingLevel;
    }

    /**
     * Returns a string representation of this linting item.
     * Subclasses should override this method to provide more detailed information.
     *
     * @return string representation for console output
     */
    @Override
    public String toString() {
        return "[" + severity + "] " + type + ": " + getDescription();
    }
}
