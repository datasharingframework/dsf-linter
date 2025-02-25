package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

/**
 * FHIR element validation item.
 * This class extends {@link FhirValidationItem} by adding additional attributes
 * that describe a specific FHIR element.
 */
public abstract class FhirElementValidationItem extends FhirValidationItem {

    /**
     * Constructs a new FHIR element validation item.
     *
     * @param severity      the validation severity
     */
    public FhirElementValidationItem(ValidationSeverity severity) {
        super(severity);
    }

}
