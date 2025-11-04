package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that no "extension-process-authorization" was found in the FHIR resource.
 * <p>
 * This Lint Item corresponds to
 * {@link LintingType#NO_EXTENSION_PROCESS_AUTHORIZATION_FOUND} and is used to signal that
 * the expected FHIR extension for process authorization is missing from the resource.
 * </p>
 */
public class FhirNoExtensionProcessAuthorizationFoundLintItem extends FhirElementLintItem {
    /**
     * Constructs a new Lint Item indicating that the "extension-process-authorization"
     * was not found in the given FHIR resource.
     *
     * @param resourceFile  the FHIR resource file where the extension was expected
     * @param fhirReference a canonical URL or local reference identifying the FHIR resource
     */
    public FhirNoExtensionProcessAuthorizationFoundLintItem(File resourceFile, String fhirReference) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference,
                LintingType.NO_EXTENSION_PROCESS_AUTHORIZATION_FOUND,
                "No extension-process-authorization found in file: " + resourceFile.getName());
    }

    /**
     * Constructs a new Lint Item indicating that the "extension-process-authorization"
     * was not found in the given FHIR resource, with a custom description.
     *
     * @param resourceFile  the FHIR resource file where the extension was expected
     * @param fhirReference a canonical URL or local reference identifying the FHIR resource
     * @param description   a custom message describing the Lint issue
     */
    public FhirNoExtensionProcessAuthorizationFoundLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference,
                LintingType.NO_EXTENSION_PROCESS_AUTHORIZATION_FOUND,
                description);
    }

    /**
     * Returns a formatted string representation of this Lint Item.
     *
     * @return a string containing the severity, lint type, FHIR reference, file name, and description
     */
    @Override
    public String toString() {
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
