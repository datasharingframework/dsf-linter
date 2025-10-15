package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code ValueSet.compose.include.system} URL does not match
 * the canonical {@code CodeSystem.url} that actually contains the referenced {@code concept.code}.
 *
 * <p>Typical trigger:
 * If a ValueSet includes a {@code system} that is not found, or is found but does not contain the
 * requested code, the validator searches all known CodeSystems for that code. When the code exists
 * elsewhere, this item is raised to point out the incorrect {@code system} reference.</p>
 *
 * <p>Example description:
 * <pre>
 * code 'abc' exists in system(s) [http://example.org/CodeSystem/real] but ValueSet references 'http://wrong.system'
 * </pre>
 * </p>
 *
 * <p>Severity: {@link ValidationSeverity#ERROR}</p>
 */
public class FhirValueSetFalseUrlReferencedValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation error for a ValueSet that references a {@code system} URL
     * which does not correspond to the CodeSystem actually containing the code.
     *
     * @param resourceFile  the FHIR resource file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the ValueSet
     * @param description   a human-readable explanation (e.g., which systems actually contain the code)
     */
    public FhirValueSetFalseUrlReferencedValidationItem(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_FALSE_URL_REFERENCED,
                description
        );
    }

    /**
     * Constructs a new validation error for a ValueSet that references a {@code system} URL
     * which does not correspond to the CodeSystem actually containing the code, explicitly
     * passing the resolved resourceId (if available).
     *
     * @param resourceFile  the FHIR resource file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the ValueSet
     * @param description   a human-readable explanation (e.g., which systems actually contain the code)
     * @param resourceId    the resolved resource ID for the ValueSet, or {@code null} to derive it
     */
    public FhirValueSetFalseUrlReferencedValidationItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_VALUE_SET_FALSE_URL_REFERENCED,
                description,
                resourceId
        );
    }

    @Override
    public String toString()
    {
        return String.format(
                "[%s] %s (fhirReference=%s, file=%s, description=%s)",
                getSeverity(),
                this.getClass().getSimpleName(),
                getFhirReference(),
                getResourceFile(),
                getDescription()
        );
    }
}
