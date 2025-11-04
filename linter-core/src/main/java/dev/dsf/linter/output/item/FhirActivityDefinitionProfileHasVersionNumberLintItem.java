package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the ActivityDefinition profile contains a version number,
 * which is not allowed. The profile URL should not contain a version suffix like '|1.0.0'.
 * Corresponds to {@link LintingType#ACTIVITY_DEFINITION_PROFILE_NO_PLACEHOLDER}.
 */
public class FhirActivityDefinitionProfileHasVersionNumberLintItem extends FhirElementLintItem {
    /**
     * Constructs a new Lint Item for a profile with version number using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirActivityDefinitionProfileHasVersionNumberLintItem(File resourceFile, String fhirReference) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.ACTIVITY_DEFINITION_PROFILE_NO_PLACEHOLDER,
                "ActivityDefinition profile must not contain a version number. Use 'http://dsf.dev/fhir/StructureDefinition/activity-definition' without '|x.x.x'.");
    }

    /**
     * Constructs a new Lint Item for a profile with version number using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom lint description describing the issue
     */
    public FhirActivityDefinitionProfileHasVersionNumberLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.ACTIVITY_DEFINITION_PROFILE_NO_PLACEHOLDER,
                description);
    }
}