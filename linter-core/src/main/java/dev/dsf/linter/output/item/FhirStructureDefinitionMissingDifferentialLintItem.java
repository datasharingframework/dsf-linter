package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating that a {@code differential} section is missing
 * from a StructureDefinition, which is required for DSF authoring.
 *
 * <p>Corresponds to {@link LintingType#STRUCTURE_DEFINITION_DIFFERENTIAL_MISSING}.</p>
 */
public class FhirStructureDefinitionMissingDifferentialLintItem extends FhirElementLintItem
{
    public FhirStructureDefinitionMissingDifferentialLintItem(File resourceFile, String fhirReference)
    {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_DIFFERENTIAL_MISSING,
                "Missing <differential> section"
        );
    }
}
