package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code differential} section is missing
 * from a StructureDefinition, which is required for DSF authoring.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_DIFFERENTIAL_MISSING}.</p>
 */
public class FhirStructureDefinitionMissingDifferentialItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionMissingDifferentialItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_DIFFERENTIAL_MISSING,
                "Missing <differential> section"
        );
    }
}
