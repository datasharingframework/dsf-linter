package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the ActivityDefinition is missing the expected profile
 * in the meta section.
 * Corresponds to {@link LintingType#ACTIVITY_DEFINITION_MISSING_PROFILE}.
 */
public class FhirActivityDefinitionMissingProfileLintItem extends FhirElementLintItem {
    /**
     * Constructs a new Lint Item for a missing profile using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirActivityDefinitionMissingProfileLintItem(File resourceFile, String fhirReference) {
        super(LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.ACTIVITY_DEFINITION_MISSING_PROFILE,
                "ActivityDefinition is missing the expected profile 'http://dsf.dev/fhir/StructureDefinition/activity-definition' in <meta><profile>.");
    }

    /**
     * Constructs a new Lint Item for a missing profile using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom lint description describing the issue
     */
    public FhirActivityDefinitionMissingProfileLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.ACTIVITY_DEFINITION_MISSING_PROFILE,
                description);
    }
}