package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;
import java.io.File;

/**
 * Validation warning indicating that the <code>date</code> field of a FHIR CodeSystem
 * does not contain the required placeholder <code>#{date}</code>.
 * <p>
 * In DSF CodeSystems, the <code>date</code> field must include this placeholder during
 * development so it can be replaced automatically by the build pipeline.
 * </p>
 */
public class FhirCodeSystemDateNoPlaceholderValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new warning indicating that the <code>date</code> field is missing
     * the <code>#{date}</code> placeholder in the given CodeSystem.
     *
     * @param resourceFile   the FHIR CodeSystem file where the issue was detected
     * @param fhirReference  a canonical or logical reference to the resource
     */
    public FhirCodeSystemDateNoPlaceholderValidationItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.CODE_SYSTEM_DATE_NO_PLACEHOLDER,
                "The <date> element is missing the placeholder '#{date}'."
        );
    }

    /**
     * Constructs a new warning with a custom message for a missing <code>#{date}</code> placeholder.
     *
     * @param resourceFile   the FHIR CodeSystem file where the issue was detected
     * @param fhirReference  a canonical or logical reference to the resource
     * @param description    explanation of the issue
     */
    public FhirCodeSystemDateNoPlaceholderValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.CODE_SYSTEM_DATE_NO_PLACEHOLDER,
                description
        );
    }
}
