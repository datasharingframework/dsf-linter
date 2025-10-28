package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that the {@code <intent>} value of a FHIR {@code Task} resource
 * is not set to the expected fixed value {@code "order"}.
 *
 * <p>According to the DSF {@code task-base} profile, {@code Task.intent} must always be
 * set to {@code "order"}. Any other value is invalid and violates the constraint defined in
 * the StructureDefinition.</p>
 *
 * <p>This validation issue corresponds to {@link LintingType#TASK_INTENT_NOT_ORDER}.</p>
 */
public class FhirTaskValueIsNotSetAsOrderLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item indicating that the {@code intent} field is not set to {@code "order"}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the validation issue
     */
    public FhirTaskValueIsNotSetAsOrderLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.TASK_INTENT_NOT_ORDER,
                description);
    }

    /**
     * Constructs a validation item using the default error message for {@code intent ≠ "order"}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskValueIsNotSetAsOrderLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference, "Task.intent must be fixed to 'order'.");
    }
}
