package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

// FHIR Validation Items
public abstract class FhirValidationItem extends AbstractValidationItem {
    public FhirValidationItem(ValidationSeverity severity) {
        super(severity);
    }

    public abstract String getDescription();
}
