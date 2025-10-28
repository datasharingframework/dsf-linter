package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a FHIR {@code Task} resource contains an unknown
 * or unsupported {@code code} in one of its elements.
 *
 * <p>According to the DSF process profiles, only predefined codes are allowed
 * in specific {@code code} elements such as {@code Task.code}, {@code input.type.coding.code},
 * or {@code output.type.coding.code}.</p>
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_UNKNOWN_CODE}.</p>
 */
public class FhirTaskUnknownCodeValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item indicating an unknown or unsupported {@code code}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     * @param description   a human-readable description of the issue
     */
    public FhirTaskUnknownCodeValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_UNKNOWN_CODE,
                description);
    }

    /**
     * Constructs a validation item using the default message for an unknown {@code code}.
     *
     * @param resourceFile  the file in which the FHIR Task resource was found
     * @param fhirReference the canonical URL or local identifier of the resource
     */
    public FhirTaskUnknownCodeValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Task contains unknown or unsupported code in a coding element.");
    }
}
