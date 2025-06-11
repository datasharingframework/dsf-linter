package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains an {@code input}
 * slice with {@code code=business-key}, and the status is not {@code draft}.
 *
 * <p>This is informational and confirms that the {@code business-key} is present when allowed
 * (i.e., in non-draft states).</p>
 *
 * <p>This validation result corresponds to {@link ValidationType#Fhir_TASK_BUSINESS_KEY_PRESENT_AND_STATUS_VALID}.</p>
 */
public class FhirTaskBusinessKeyExistsAndStatusNotDraftValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs an informational validation item confirming presence of {@code business-key}
     * when allowed.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the result
     */
    public FhirTaskBusinessKeyExistsAndStatusNotDraftValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_BUSINESS_KEY_PRESENT_AND_STATUS_VALID,
                description);
    }

    /**
     * Constructs a validation item using the default message confirming that
     * {@code business-key} is present and allowed in current Task {@code status}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskBusinessKeyExistsAndStatusNotDraftValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task.status is not 'draft' and 'business-key' input is present as expected.");
    }
}
