package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Represents a FHIR lint error indicating that an organization-role extension
 * contains an invalid code value that is not recognized in the DSF organization-role CodeSystem.
 */
public class FhirValueSetOrganizationRoleMissingValidCodeValueLintItem extends FhirElementLintItem {
    /**
     * Constructs a new validation error item indicating an invalid organization-role code.
     *
     * @param resourceFile  the FHIR ValueSet file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the FHIR ValueSet
     * @param description   a human-readable description of the error
     */
    public FhirValueSetOrganizationRoleMissingValidCodeValueLintItem(
            File resourceFile,
            String fhirReference,
            String description) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_VALUE_SET_ORGANIZATION_ROLE_MISSING_VALID_CODE_VALUE,
                description
        );
    }

    /**
     * Constructs a new validation error item indicating an invalid organization-role code
     * with an explicit FHIR resource ID.
     *
     * @param resourceFile  the FHIR ValueSet file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the FHIR ValueSet
     * @param description   a human-readable description of the error
     * @param resourceId    the ID of the ValueSet resource
     */
    public FhirValueSetOrganizationRoleMissingValidCodeValueLintItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_VALUE_SET_ORGANIZATION_ROLE_MISSING_VALID_CODE_VALUE,
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

