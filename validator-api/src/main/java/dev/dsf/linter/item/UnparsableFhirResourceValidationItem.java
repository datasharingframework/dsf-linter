package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;

// Unparsable Validation Items
public class UnparsableFhirResourceValidationItem extends AbstractValidationItem {
    public UnparsableFhirResourceValidationItem(ValidationSeverity severity) {
        super(severity);
    }
}
