package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Represents a FHIR lint error indicating that the required
 * <code>meta.tag</code> with system {@code http://dsf.dev/fhir/CodeSystem/read-access-tag}
 * and code {@code LOCAL} or {@code ALL} is missing in a ValueSet resource.
 */
public class FhirValueSetMissingReadAccessTagAllOrLocalLintItem extends FhirElementLintItem {
    /**
     * Constructs a new lint error item indicating a missing read-access-tag
     * with LOCAL or ALL code in the meta.tag of a ValueSet.
     *
     * @param resourceFile  the FHIR ValueSet file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the FHIR ValueSet
     * @param description   a human-readable description of the error
     */
    public FhirValueSetMissingReadAccessTagAllOrLocalLintItem(
            File resourceFile,
            String fhirReference,
            String description) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_VALUE_SET_MISSING_READ_ACCESS_TAG_ALL_OR_LOCAL,
                description
        );
    }

    /**
     * Constructs a new lint error item indicating a missing read-access-tag
     * with LOCAL or ALL code with an explicit FHIR resource ID.
     *
     * @param resourceFile  the FHIR ValueSet file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the FHIR ValueSet
     * @param description   a human-readable description of the error
     * @param resourceId    the ID of the ValueSet resource
     */
    public FhirValueSetMissingReadAccessTagAllOrLocalLintItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_VALUE_SET_MISSING_READ_ACCESS_TAG_ALL_OR_LOCAL,
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

