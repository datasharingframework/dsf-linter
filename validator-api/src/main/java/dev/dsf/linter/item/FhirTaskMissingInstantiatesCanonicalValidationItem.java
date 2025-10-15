package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource is missing the required
 * {@code <instantiatesCanonical>} element.
 *
 * <p>According to the DSF {@code task-base} profile, the element {@code instantiatesCanonical}
 * must be present and reference the canonical URL of the associated {@code ActivityDefinition}.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#FHIR_TASK_MISSING_INSTANTIATES_CANONICAL}.</p>
 */
public class FhirTaskMissingInstantiatesCanonicalValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating a missing {@code <instantiatesCanonical>} element.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the problem
     */
    public FhirTaskMissingInstantiatesCanonicalValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.FHIR_TASK_MISSING_INSTANTIATES_CANONICAL,
                description);
    }

    /**
     * Constructs a validation item using a default description for the missing {@code instantiatesCanonical}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskMissingInstantiatesCanonicalValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference, "Task is missing <instantiatesCanonical>.");
    }
}
