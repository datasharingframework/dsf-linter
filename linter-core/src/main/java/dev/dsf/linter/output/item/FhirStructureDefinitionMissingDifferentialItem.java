package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

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
