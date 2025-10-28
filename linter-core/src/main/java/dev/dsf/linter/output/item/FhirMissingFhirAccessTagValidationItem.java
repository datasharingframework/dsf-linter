package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the FHIR access tag is missing.
 * Corresponds to {@link ValidationType#MISSING_FHIR_ACCESS_TAG}.
 */
public class FhirMissingFhirAccessTagValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item for a missing FHIR access tag using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirMissingFhirAccessTagValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.MISSING_FHIR_ACCESS_TAG,
                "Missing FHIR access tag");
    }

    /**
     * Constructs a new validation item for a missing FHIR access tag using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom validation message describing the issue
     */
    public FhirMissingFhirAccessTagValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.MISSING_FHIR_ACCESS_TAG,
                description);
    }
}
