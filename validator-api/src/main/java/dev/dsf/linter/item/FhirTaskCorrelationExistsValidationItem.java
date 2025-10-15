package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains a
 * {@code correlation-key} input slice, which is not allowed.
 *
 * <p>According to the DSF {@code task-base} profile, {@code correlation-key}
 * must not be present in {@code Task.input}.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_CORRELATION_KEY_EXISTS}.</p>
 */
public class FhirTaskCorrelationExistsValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating disallowed {@code correlation-key} input.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskCorrelationExistsValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_CORRELATION_KEY_EXISTS,
                description);
    }

    /**
     * Constructs a validation item using the default message for {@code correlation-key} presence.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskCorrelationExistsValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task contains a 'correlation-key' input which is not allowed.");
    }
}
