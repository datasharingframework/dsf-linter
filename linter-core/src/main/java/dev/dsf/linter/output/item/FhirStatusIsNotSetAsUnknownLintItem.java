package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating that the FHIR status is unknown.
 * Corresponds to {@link LintingType#FHIR_STATUS_IS_NOT_SET_AS_UNKNOWN}.
 */
public class FhirStatusIsNotSetAsUnknownLintItem extends FhirElementLintItem
{
    /**
     * Constructs a new linting item with a default description.
     *
     * @param resourceFile the file where the resource was loaded from
     * @param fhirStatus   the FHIR status (e.g., a string representing the status)
     */
    public FhirStatusIsNotSetAsUnknownLintItem(File resourceFile, String fhirStatus)
    {
        super(LinterSeverity.WARN, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirStatus, LintingType.FHIR_STATUS_IS_NOT_SET_AS_UNKNOWN,
                "FHIR status is not set as unknown");
    }

    /**
     * Constructs a new linting item with a custom description.
     *
     * @param resourceFile the file where the resource was loaded from
     * @param fhirStatus   the FHIR status (e.g., a string representing the status)
     * @param description  a custom linting description
     */
    public FhirStatusIsNotSetAsUnknownLintItem(File resourceFile, String fhirStatus, String description)
    {
        super(LinterSeverity.WARN, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirStatus, LintingType.FHIR_STATUS_IS_NOT_SET_AS_UNKNOWN, description);
    }
}
