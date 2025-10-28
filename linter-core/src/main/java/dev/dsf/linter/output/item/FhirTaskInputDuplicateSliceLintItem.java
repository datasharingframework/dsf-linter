package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains duplicate
 * slices (e.g., multiple {@code input} elements with the same {@code type.coding.code}).
 *
 * <p>According to the DSF {@code task-base} profile, certain input slices must occur exactly once.
 * Duplicate slices with the same identifying code (e.g., {@code business-key}) are not allowed.</p>
 *
 * <p>This validation issue corresponds to {@link LintingType#Fhir_TASK_DUPLICATE_SLICE}.</p>
 */
public class FhirTaskInputDuplicateSliceLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item indicating duplicate slices in the {@code Task} resource.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskInputDuplicateSliceLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_DUPLICATE_SLICE,
                description);
    }

    /**
     * Constructs a validation item using the default message for a duplicate slice issue.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskInputDuplicateSliceLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Task contains duplicate input slices with the same identifying code.");
    }
}
