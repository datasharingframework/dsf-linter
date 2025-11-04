package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the {@code <restriction.recipient.identifier.system>} element
 * of a FHIR {@code Task} resource is invalid.
 *
 * <p>According to the DSF {@code task-base} profile, the recipient's identifier system must be
 * {@code http://dsf.dev/sid/organization-identifier} to correctly reference the organization.</p>
 *
 * <p>This Lint issue corresponds to {@link LintingType#INVALID_TASK_RECIPIENT_SYSTEM}.</p>
 */
public class FhirTaskInvalidRecipientLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item for an invalid recipient identifier system with a custom message.
     *
     * @param resourceFile  the file where the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable validation message
     */
    public FhirTaskInvalidRecipientLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.INVALID_TASK_RECIPIENT_SYSTEM,
                description);
    }

    /**
     * Constructs a Lint Item using the default message for an invalid recipient identifier system.
     *
     * @param resourceFile  the file where the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskInvalidRecipientLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task.restriction.recipient.identifier.system must be 'http://dsf.dev/sid/organization-identifier'.");
    }
}
