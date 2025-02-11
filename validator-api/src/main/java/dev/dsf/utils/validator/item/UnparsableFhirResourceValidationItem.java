package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

// Unparsable Validation Items
public class UnparsableFhirResourceValidationItem extends AbstractValidationItem {
    public UnparsableFhirResourceValidationItem(ValidationSeverity severity) {
        super(severity);
    }
}
