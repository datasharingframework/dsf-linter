package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

// BPMN Validation Items
public abstract class BpmnValidationItem extends AbstractValidationItem {
    public BpmnValidationItem(ValidationSeverity severity) {
        super(severity);
    }
}
