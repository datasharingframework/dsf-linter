package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a FHIR {@code Task} resource contains no
 * {@code restriction.recipient.identifier.value} element.
 *
 * <p>According to the DSF {@code task-base} profile (development context),
 * the {@code restriction.recipient.identifier.value} should be set. Missing this element
 * means the recipient organization cannot be resolved.</p>
 *
 * <p>This Lint issue corresponds to {@link LintingType#Fhir_TASK_RECIPIENT_ID_MISSING}.</p>
 */
public class FhirTaskRecipientIdNotExistLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item with a custom message.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param message       the message to describe the Lint issue
     */
    public FhirTaskRecipientIdNotExistLintItem(File resourceFile, String fhirReference, String message) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_RECIPIENT_ID_MISSING,
                message);
    }

    /**
     * Constructs a Lint Item using the default message for a missing
     * {@code restriction.recipient.identifier.value}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskRecipientIdNotExistLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task.restriction.recipient.identifier.value is missing.");
    }
}
