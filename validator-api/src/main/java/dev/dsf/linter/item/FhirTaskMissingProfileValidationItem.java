package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource is missing the required
 * {@code <meta.profile>} element or that it is empty.
 *
 * <p>According to the FHIR specification and the DSF {@code task-base} profile,
 * the {@code meta.profile} element is mandatory and must contain the profile URL.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_MISSING_PROFILE}.</p>
 */
public class FhirTaskMissingProfileValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating a missing or empty {@code <meta.profile>} element.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskMissingProfileValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_MISSING_PROFILE,
                description);
    }

    /**
     * Constructs a validation item using the default error message for a missing {@code meta.profile}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskMissingProfileValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference, "Task is missing <meta.profile> or it is empty.");
    }
}
