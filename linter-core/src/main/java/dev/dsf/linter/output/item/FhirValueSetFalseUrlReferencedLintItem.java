package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a {@code ValueSet.compose.include.system} URL does not match
 * the canonical {@code CodeSystem.url} that actually contains the referenced {@code concept.code}.
 *
 * <p>Typical trigger:
 * If a ValueSet includes a {@code system} that is not found, or is found but does not contain the
 * requested code, the linter searches all known CodeSystems for that code. When the code exists
 * elsewhere, this item is raised to point out the incorrect {@code system} reference.</p>
 *
 * <p>Example description:
 * <pre>
 * code 'abc' exists in system(s) [http://example.org/CodeSystem/real] but ValueSet references 'http://wrong.system'
 * </pre>
 * </p>
 *
 * <p>Severity: {@link LinterSeverity#ERROR}</p>
 */
public class FhirValueSetFalseUrlReferencedLintItem extends FhirElementLintItem {
    /**
     * Constructs a new lint error for a ValueSet that references a {@code system} URL
     * which does not correspond to the CodeSystem actually containing the code.
     *
     * @param resourceFile  the FHIR resource file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the ValueSet
     * @param description   a human-readable explanation (e.g., which systems actually contain the code)
     */
    public FhirValueSetFalseUrlReferencedLintItem(
            File resourceFile,
            String fhirReference,
            String description) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_VALUE_SET_FALSE_URL_REFERENCED,
                description
        );
    }

    /**
     * Constructs a new lint error for a ValueSet that references a {@code system} URL
     * which does not correspond to the CodeSystem actually containing the code, explicitly
     * passing the resolved resourceId (if available).
     *
     * @param resourceFile  the FHIR resource file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the ValueSet
     * @param description   a human-readable explanation (e.g., which systems actually contain the code)
     * @param resourceId    the resolved resource ID for the ValueSet, or {@code null} to derive it
     */
    public FhirValueSetFalseUrlReferencedLintItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_VALUE_SET_FALSE_URL_REFERENCED,
                description,
                resourceId
        );
    }

    @Override
    public String toString() {
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
