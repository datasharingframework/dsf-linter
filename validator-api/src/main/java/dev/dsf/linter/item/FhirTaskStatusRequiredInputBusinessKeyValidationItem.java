package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code Task.input} element with {@code type.coding.code = "business-key"}
 * is missing, even though the {@code Task.status} value requires it.
 *
 * <p>According to the DSF {@code task-base} profile, if {@code Task.status} is set to one of
 * {@code in-progress}, {@code completed}, or {@code failed}, then the {@code business-key}
 * input slice must be present.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#TASK_BUSINESS_KEY_REQUIRED_FOR_STATUS}.</p>
 */
public class FhirTaskStatusRequiredInputBusinessKeyValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item using a custom message for a missing 'business-key' when required by {@code status}.
     *
     * @param resourceFile  the file where the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a custom message describing the validation issue
     */
    public FhirTaskStatusRequiredInputBusinessKeyValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.TASK_BUSINESS_KEY_REQUIRED_FOR_STATUS,
                description);
    }

    /**
     * Constructs a validation item using the default message for missing 'business-key' required by {@code status}.
     *
     * @param resourceFile  the file where the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskStatusRequiredInputBusinessKeyValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task.status is one of 'in-progress', 'completed', or 'failed', so a 'business-key' input is required but missing.");
    }
}
