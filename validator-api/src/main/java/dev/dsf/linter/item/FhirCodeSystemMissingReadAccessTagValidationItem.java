package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a CodeSystem resource is missing the required
 * read access tag (system = http://dsf.dev/fhir/CodeSystem/read-access-tag, code = ALL).
 *
 * <p>This validation rule is part of the DSF CodeSystem profile and ensures that
 * all CodeSystems specify the expected read-access policy.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#MISSING_READ_ACCESS_TAG}.</p>
 */
public class FhirCodeSystemMissingReadAccessTagValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item using the default error message for a missing read access tag.
     *
     * @param resourceFile  the file in which the CodeSystem resource was found
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     */
    public FhirCodeSystemMissingReadAccessTagValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "CodeSystem is missing required meta.tag with system 'http://dsf.dev/fhir/CodeSystem/read-access-tag' and code 'ALL'.");
    }

    /**
     * Constructs a validation item with a custom error description.
     *
     * @param resourceFile  the file in which the CodeSystem resource was found
     * @param fhirReference the canonical URL or local reference of the CodeSystem
     * @param description   custom message describing the validation issue
     */
    public FhirCodeSystemMissingReadAccessTagValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.MISSING_READ_ACCESS_TAG,
                description);
    }
}
