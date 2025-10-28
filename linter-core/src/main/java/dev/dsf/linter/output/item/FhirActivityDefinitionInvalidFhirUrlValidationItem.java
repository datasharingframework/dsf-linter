package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the FHIR URL is invalid.
 * Corresponds to {@link ValidationType#INVALID_FHIR_URL}.
 */
public class FhirActivityDefinitionInvalidFhirUrlValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item with a default description.
     *
     * @param resourceFile  the file where the resource was loaded from
     * @param fhirReference the FHIR reference (e.g., URL)
     */
    public FhirActivityDefinitionInvalidFhirUrlValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, ValidationType.INVALID_FHIR_URL,
                "FHIR URL is invalid");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param resourceFile  the file where the resource was loaded from
     * @param fhirReference the FHIR reference (e.g., URL)
     * @param description   a custom validation description
     */
    public FhirActivityDefinitionInvalidFhirUrlValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, ValidationType.INVALID_FHIR_URL, description);
    }
}
