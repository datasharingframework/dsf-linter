package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains an unknown
 * or unsupported {@code code} in one of its elements.
 *
 * <p>According to the DSF process profiles, only predefined codes are allowed
 * in specific {@code code} elements such as {@code Task.code}, {@code input.type.coding.code},
 * or {@code output.type.coding.code}.</p>
 *
 * <p>This validation issue corresponds to {@link LintingType#Fhir_TASK_UNKNOWN_CODE}.</p>
 */
public class FhirTaskUnknownCodeLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item indicating an unknown or unsupported {@code code}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskUnknownCodeLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_UNKNOWN_CODE,
                description);
    }

    /**
     * Constructs a validation item using the default message for an unknown {@code code}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskUnknownCodeLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task contains unknown or unsupported code in a coding element.");
    }
}
