package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;
import java.io.File;

/**
 * Validation item indicating that no "extension-process-authorization" was found in the FHIR resource.
 * <p>
 * This validation item corresponds to
 * {@link ValidationType#NO_EXTENSION_PROCESS_AUTHORIZATION_FOUND} and is used to signal that
 * the expected FHIR extension for process authorization is missing from the resource.
 * </p>
 */
public class FhirNoExtensionProcessAuthorizationFoundValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item indicating that the "extension-process-authorization"
     * was not found in the given FHIR resource.
     *
     * @param resourceFile  the FHIR resource file where the extension was expected
     * @param fhirReference a canonical URL or local reference identifying the FHIR resource
     */
    public FhirNoExtensionProcessAuthorizationFoundValidationItem(File resourceFile, String fhirReference)
    {
        super(ValidationSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference,
                ValidationType.NO_EXTENSION_PROCESS_AUTHORIZATION_FOUND,
                "No extension-process-authorization found in file: " + resourceFile.getName());
    }

    /**
     * Constructs a new validation item indicating that the "extension-process-authorization"
     * was not found in the given FHIR resource, with a custom description.
     *
     * @param resourceFile  the FHIR resource file where the extension was expected
     * @param fhirReference a canonical URL or local reference identifying the FHIR resource
     * @param description   a custom message describing the validation issue
     */
    public FhirNoExtensionProcessAuthorizationFoundValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference,
                ValidationType.NO_EXTENSION_PROCESS_AUTHORIZATION_FOUND,
                description);
    }
    /**
     * Returns a formatted string representation of this validation item.
     *
     * @return a string containing the severity, validation type, FHIR reference, file name, and description
     */
    @Override
    public String toString()
    {
        return String.format(
                "[%s] %s (%s): %s [file=%s]",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFhirReference(),
                getDescription(),
                getResourceFile()
        );
    }
}
