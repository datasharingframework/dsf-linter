package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code StructureDefinition} profile referenced in a {@code Task}
 * could not be located, which prevents cardinality validation.
 *
 * <p>This validation issue corresponds to {@link ValidationType#Fhir_TASK_PROFILE_NOT_FOUND}.</p>
 */
public class FhirTaskCouldNotLoadProfileValidationItem extends FhirElementValidationItem
{
    public FhirTaskCouldNotLoadProfileValidationItem(File resourceFile, String fhirReference, String description)
    {
        super(ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.Fhir_TASK_PROFILE_NOT_FOUND,
                description != null ? description : "Referenced StructureDefinition could not be loaded.");
    }
}
