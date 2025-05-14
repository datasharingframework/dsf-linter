package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains duplicate
 * slices (e.g., multiple {@code input} elements with the same {@code type.coding.code}).
 *
 * <p>According to the DSF {@code task-base} profile, certain input slices must occur exactly once.
 * Duplicate slices with the same identifying code (e.g., {@code business-key}) are not allowed.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_DUPLICATE_SLICE}.</p>
 */
public class FhirTaskInputDuplicateSliceValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating duplicate slices in the {@code Task} resource.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskInputDuplicateSliceValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_DUPLICATE_SLICE,
                description);
    }

    /**
     * Constructs a validation item using the default message for a duplicate slice issue.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskInputDuplicateSliceValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task contains duplicate input slices with the same identifying code.");
    }
}
