package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ProcessingLevel;
import dev.dsf.linter.output.ValidationSeverity;

public abstract class AbstractValidationItem {
    private final ValidationSeverity severity;
    private final ProcessingLevel processingLevel = ProcessingLevel.FILE;

    public AbstractValidationItem(ValidationSeverity severity) {
        this.severity = severity;
    }

    public ValidationSeverity getSeverity() {
        return severity;
    }

    /**
     * Returns a string representation of this validation item.
     * Subclasses should override this method to provide more detailed information.
     *
     * @return string representation for console output
     */
    @Override
    public String toString() {
        return "[" + severity + "] " + this.getClass().getSimpleName();
    }
}
