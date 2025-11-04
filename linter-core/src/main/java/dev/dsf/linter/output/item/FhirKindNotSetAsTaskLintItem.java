package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Represents a FHIR lint error indicating that the resource kind is not set as Task.
 * <p>
 * This item is used to flag FHIR resources that do not meet the requirement
 * to have their kind defined as Task.
 * </p>
 */
public class FhirKindNotSetAsTaskLintItem extends FhirElementLintItem {
    /**
     * Constructs a new lint error item for FHIR resources that are not defined as a Task.
     *
     * @param resourceFile  the FHIR resource file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the FHIR resource
     * @param description   a human-readable description of the error
     */
    public FhirKindNotSetAsTaskLintItem(
            File resourceFile,
            String fhirReference,
            String description) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.FHIR_KIND_NOT_SET_AS_TASK, description);
    }

    /**
     * Constructs a new lint error item for FHIR resources that are not defined as a Task,
     * including a known resource ID.
     *
     * @param resourceFile  the FHIR resource file where the issue was detected
     * @param fhirReference a canonical URL or local reference identifying the FHIR resource
     * @param description   a human-readable description of the error
     * @param resourceId    the FHIR resource ID, if known
     */
    public FhirKindNotSetAsTaskLintItem(
            File resourceFile,
            String fhirReference,
            String description,
            String resourceId) {
        super(LinterSeverity.ERROR, resourceFile != null ? resourceFile.getName() : "unknown.xml", fhirReference, LintingType.FHIR_KIND_NOT_SET_AS_TASK, description, resourceId);
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
