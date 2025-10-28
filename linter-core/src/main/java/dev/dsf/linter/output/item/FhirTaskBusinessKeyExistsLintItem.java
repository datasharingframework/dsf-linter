package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains an unexpected
 * {@code input} slice with {@code code=business-key}.
 *
 * <p>According to the DSF {@code task-base} profile, {@code business-key} input must
 * not be present when {@code status=draft}.</p>
 *
 * <p>This validation issue corresponds to {@link LintingType#Fhir_TASK_BUSINESS_KEY_EXISTS}.</p>
 */
public class FhirTaskBusinessKeyExistsLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item indicating that a {@code business-key} input exists
     * when it must not.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskBusinessKeyExistsLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_BUSINESS_KEY_EXISTS,
                description);
    }

    /**
     * Constructs a validation item using the default message for unexpected {@code business-key} presence.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskBusinessKeyExistsLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task contains a 'business-key' input when it must not be present.");
    }
}
