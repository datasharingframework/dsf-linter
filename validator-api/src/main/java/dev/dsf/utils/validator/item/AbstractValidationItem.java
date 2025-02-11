package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ProcessingLevel;
import dev.dsf.utils.validator.ValidationSeverity;

public abstract class AbstractValidationItem {
    private final ValidationSeverity severity;
    private final ProcessingLevel processingLevel = ProcessingLevel.FILE;

    public AbstractValidationItem(ValidationSeverity severity) {
        this.severity = severity;
    }

    public ValidationSeverity getSeverity() {
        return severity;
    }
}
