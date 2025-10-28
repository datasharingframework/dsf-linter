package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

// Unparsable Validation Items
public class UnparsableFhirResourceLintItem extends AbstractLintItem {
    public UnparsableFhirResourceLintItem(LinterSeverity severity) {
        super(severity);
    }
}
