package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;

// Unparsable Validation Items
public class UnparsableFhirResourceValidationItem extends AbstractValidationItem {
    public UnparsableFhirResourceValidationItem(ValidationSeverity severity) {
        super(severity);
    }
}
