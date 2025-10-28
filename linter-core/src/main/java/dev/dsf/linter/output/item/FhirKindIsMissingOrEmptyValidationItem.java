package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the FHIR kind is invalid.
 * Corresponds to {@link ValidationType#INVALID_FHIR_KIND}.
 */
public class FhirKindIsMissingOrEmptyValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item with a default description.
     *
     * @param resourceFile the file where the resource was loaded from
     * @param fhirKind     the FHIR kind (e.g., a string representing the kind)
     */
    public FhirKindIsMissingOrEmptyValidationItem(File resourceFile, String fhirKind)
    {
        super(ValidationSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirKind, ValidationType.INVALID_FHIR_KIND,
                "FHIR kind is missing or empty");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param resourceFile the file where the resource was loaded from
     * @param fhirKind     the FHIR kind (e.g., a string representing the kind)
     * @param description  a custom validation description
     */
    public FhirKindIsMissingOrEmptyValidationItem(File resourceFile, String fhirKind, String description)
    {
        super(ValidationSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirKind, ValidationType.INVALID_FHIR_KIND, description);
    }
}
