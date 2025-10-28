package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code url} element of a CodeSystem resource
 * does not begin with the required DSF namespace:
 * {@code http://dsf.dev/fhir/CodeSystem/}.
 *
 * <p>This check is required by the DSF CodeSystem base profile and ensures that
 * all CodeSystems follow the canonical DSF naming scheme.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#CODE_SYSTEM_INVALID_URL}.</p>
 */
@Deprecated
public class FhirCodeSystemInvalidUrlValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item using the default error message for invalid CodeSystem URL.
     *
     * @param resourceFile  the file where the CodeSystem resource is defined
     * @param fhirReference the canonical or local reference of the CodeSystem
     */
    public FhirCodeSystemInvalidUrlValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "CodeSystem <url> must start with 'http://dsf.dev/fhir/CodeSystem/'.");
    }

    /**
     * Constructs a validation item using a custom error message for invalid CodeSystem URL.
     *
     * @param resourceFile  the file where the CodeSystem resource is defined
     * @param fhirReference the canonical or local reference of the CodeSystem
     * @param description   a custom error message
     */
    public FhirCodeSystemInvalidUrlValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.CODE_SYSTEM_INVALID_URL,
                description);
    }
}
