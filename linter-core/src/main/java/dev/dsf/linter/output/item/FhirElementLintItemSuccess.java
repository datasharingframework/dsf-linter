package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Represents a successful FHIR linting result.
 * <p>
 * This class extends {@link FhirElementLintItem} and fixes its
 * {@link LinterSeverity} to {@code SUCCESS} as well as the {@link LintingType} to {@code SUCCESS}.
 * </p>
 * <p>
 * References:
 * <ul>
 *   <li>
 *     HL7 FHIR Overview:
 *     <a href="https://hl7.org/fhir/overview.html">https://hl7.org/fhir/overview.html</a>
 *   </li>
 * </ul>
 * </p>
 */
public class FhirElementLintItemSuccess extends FhirElementLintItem
{
    /**
     * Constructs a new success Lint Item for FHIR with the given parameters.
     *
     * @param resourceFile   the FHIR resource file being validated
     * @param fhirReference  a canonical URL or reference identifying the FHIR resource
     * @param description    a short message describing the successful linting result
     */
    public FhirElementLintItemSuccess(
            File resourceFile,
            String fhirReference,
            String description)
    {
        super(LinterSeverity.SUCCESS, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.SUCCESS, description);
    }

    /**
     * Constructs a new success Lint Item for FHIR with the given parameters, including a resource ID.
     *
     * @param resourceFile   the FHIR resource file being validated
     * @param fhirReference  a canonical URL or reference identifying the FHIR resource
     * @param description    a short message describing the successful linting result
     * @param resourceId     the FHIR resource ID, if known
     */
    public FhirElementLintItemSuccess(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId)
    {
        super(LinterSeverity.SUCCESS, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.SUCCESS, description, resourceId);
    }

    @Override
    public String toString()
    {
        return String.format(
                "[%s] %s (fhirReference=%s, file=%s, description=%s)",
                LinterSeverity.SUCCESS,
                this.getClass().getSimpleName(),
                getFhirReference(),
                getResourceFile(),
                getDescription()
        );
    }
}
