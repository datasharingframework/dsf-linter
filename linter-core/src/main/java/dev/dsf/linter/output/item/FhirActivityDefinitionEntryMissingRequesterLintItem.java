package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the ActivityDefinition entry is missing the 'requester' extension
 * in the process-authorization component.
 * Corresponds to {@link LintingType#MISSING_REQUESTER_EXTENSION}.
 */
public class FhirActivityDefinitionEntryMissingRequesterLintItem extends FhirElementLintItem {
    /**
     * Constructs a new Lint Item for a missing requester extension using a default description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     */
    public FhirActivityDefinitionEntryMissingRequesterLintItem(File resourceFile, String fhirReference) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.MISSING_REQUESTER_EXTENSION,
                "No <extension url='requester'> found in process-authorization.");
    }

    /**
     * Constructs a new Lint Item for a missing requester extension using a custom description.
     *
     * @param resourceFile  the file where the FHIR resource was loaded from
     * @param fhirReference a canonical URL or local reference that identifies the resource
     * @param description   a custom lint description describing the issue
     */
    public FhirActivityDefinitionEntryMissingRequesterLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.MISSING_REQUESTER_EXTENSION,
                description);
    }
}

