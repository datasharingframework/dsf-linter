package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * <p>
 * Lint Item that represents an issue where the required
 * {@code restriction.recipient} element is missing from a FHIR Task resource.
 * </p>
 *
 * <p>
 * In DSF processes, the {@code recipient} field identifies the organization
 * authorized to receive and process the Task. This validation ensures that
 * recipient-related information is present and correctly structured.
 * </p>
 *
 * <p>
 * This item corresponds to the {@link LintingType#FHIR_TASK_MISSING_RECIPIENT}
 * type and is reported with {@link LinterSeverity#ERROR} severity.
 * </p>
 *
 * <h3>Typical Use Case:</h3>
 * <ul>
 *   <li>Validating that {@code restriction.recipient.identifier.system} exists.</li>
 *   <li>Ensuring the Task includes at least one authorized recipient organization.</li>
 * </ul>
 *
 * <h3>Example Issue:</h3>
 * <pre>
 * {
 *   "severity": "ERROR",
 *   "file": "task-example.xml",
 *   "reference": "Task/example",
 *   "type": "FHIR_TASK_MISSING_RECIPIENT",
 *   "description": "Task.restriction.recipient element is missing."
 * }
 * </pre>
 */
public class FhirTaskMissingRecipientLintItem extends FhirElementLintItem {
    /**
     * Creates a new {@code FhirTaskMissingRecipientLintItem} with a default description.
     *
     * @param resourceFile  the FHIR resource file in which the issue was found (may be {@code null})
     * @param fhirReference a canonical or local reference to the FHIR resource (e.g., {@code Task/example})
     */
    public FhirTaskMissingRecipientLintItem(File resourceFile, String fhirReference) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_TASK_MISSING_RECIPIENT,
                "Task.restriction.recipient element is missing."
        );
    }

    /**
     * Creates a new {@code FhirTaskMissingRecipientLintItem} with a custom description.
     *
     * @param resourceFile  the FHIR resource file in which the issue was found (may be {@code null})
     * @param fhirReference a canonical or local reference to the FHIR resource (e.g., {@code Task/example})
     * @param description   a custom description of the Lint issue
     */
    public FhirTaskMissingRecipientLintItem(File resourceFile, String fhirReference, String description) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.FHIR_TASK_MISSING_RECIPIENT,
                description
        );
    }
}
