package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ProcessingLevel;
import dev.dsf.linter.output.LinterSeverity;

public abstract class AbstractLintItem {
    private final LinterSeverity severity;
    private final ProcessingLevel processingLevel = ProcessingLevel.FILE;

    public AbstractLintItem(LinterSeverity severity) {
        this.severity = severity;
    }

    public LinterSeverity getSeverity() {
        return severity;
    }

    /**
     * Returns a string representation of this linting item.
     * Subclasses should override this method to provide more detailed information.
     *
     * @return string representation for console output
     */
    @Override
    public String toString() {
        return "[" + severity + "] " + this.getClass().getSimpleName();
    }
}
