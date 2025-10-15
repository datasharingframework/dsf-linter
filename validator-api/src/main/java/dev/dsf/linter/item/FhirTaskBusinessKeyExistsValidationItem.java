package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains an unexpected
 * {@code input} slice with {@code code=business-key}.
 *
 * <p>According to the DSF {@code task-base} profile, {@code business-key} input must
 * not be present when {@code status=draft}.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_BUSINESS_KEY_EXISTS}.</p>
 */
public class FhirTaskBusinessKeyExistsValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating that a {@code business-key} input exists
     * when it must not.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskBusinessKeyExistsValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_BUSINESS_KEY_EXISTS,
                description);
    }

    /**
     * Constructs a validation item using the default message for unexpected {@code business-key} presence.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskBusinessKeyExistsValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task contains a 'business-key' input when it must not be present.");
    }
}
