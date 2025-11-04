package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a FHIR {@code Task} resource contains a
 * {@code restriction.recipient.identifier.value} without the required
 * {@code #{organization}} placeholder (development context).
 *
 * <p>This Lint issue corresponds to {@link LintingType#Fhir_TASK_RECIPIENT_ID_NO_PLACEHOLDER}.</p>
 */
public class FhirTaskRecipientIdNoPlaceholderLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item with a custom message.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param message       the message to describe the Lint issue
     */
    public FhirTaskRecipientIdNoPlaceholderLintItem(File resourceFile, String fhirReference, String message) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_RECIPIENT_ID_NO_PLACEHOLDER,
                message);
    }

    /**
     * Constructs a Lint Item using the default message for a missing
     * {@code #{organization}} placeholder.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskRecipientIdNoPlaceholderLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task.restriction.recipient.identifier.value does not contain the '#{organization}' placeholder.");
    }
}
