package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a FHIR {@code Task} resource contains a
 * {@code correlation-key} input slice, which is not allowed.
 *
 * <p>According to the DSF {@code task-base} profile, {@code correlation-key}
 * must not be present in {@code Task.input}.</p>
 *
 * <p>This Lint issue corresponds to {@link LintingType#Fhir_TASK_CORRELATION_KEY_EXISTS}.</p>
 */
public class FhirTaskCorrelationExistsLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item indicating disallowed {@code correlation-key} input.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskCorrelationExistsLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_CORRELATION_KEY_EXISTS,
                description);
    }

    /**
     * Constructs a Lint Item using the default message for {@code correlation-key} presence.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskCorrelationExistsLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task contains a 'correlation-key' input which is not allowed.");
    }
}
