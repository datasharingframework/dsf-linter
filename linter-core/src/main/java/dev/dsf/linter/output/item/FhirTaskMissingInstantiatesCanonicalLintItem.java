package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a FHIR {@code Task} resource is missing the required
 * {@code <instantiatesCanonical>} element.
 *
 * <p>According to the DSF {@code task-base} profile, the element {@code instantiatesCanonical}
 * must be present and reference the canonical URL of the associated {@code ActivityDefinition}.</p>
 *
 * <p>This Lint issue corresponds to {@link LintingType#FHIR_TASK_MISSING_INSTANTIATES_CANONICAL}.</p>
 */
public class FhirTaskMissingInstantiatesCanonicalLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item indicating a missing {@code <instantiatesCanonical>} element.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the problem
     */
    public FhirTaskMissingInstantiatesCanonicalLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_TASK_MISSING_INSTANTIATES_CANONICAL,
                description);
    }

    /**
     * Constructs a Lint Item using a default description for the missing {@code instantiatesCanonical}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskMissingInstantiatesCanonicalLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference, "Task is missing <instantiatesCanonical>.");
    }
}
