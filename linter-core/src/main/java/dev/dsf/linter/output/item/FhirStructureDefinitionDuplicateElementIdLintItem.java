package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating that duplicate element {@code id} values were found
 * in the StructureDefinition differential section.
 *
 * <p>Corresponds to {@link LintingType#STRUCTURE_DEFINITION_ELEMENT_ID_DUPLICATE}.</p>
 */
public class FhirStructureDefinitionDuplicateElementIdLintItem extends FhirElementLintItem
{
    public FhirStructureDefinitionDuplicateElementIdLintItem(File resourceFile, String fhirReference, String id)
    {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_ELEMENT_ID_DUPLICATE,
                "Duplicate element @id found: '" + id + "'"
        );
    }
}
