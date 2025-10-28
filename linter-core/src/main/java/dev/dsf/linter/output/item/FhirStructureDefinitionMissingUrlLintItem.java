package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating that the {@code url} element is missing
 * from the StructureDefinition.
 *
 * <p>Corresponds to {@link LintingType#STRUCTURE_DEFINITION_URL_MISSING}.</p>
 */
public class FhirStructureDefinitionMissingUrlLintItem extends FhirElementLintItem
{
    public FhirStructureDefinitionMissingUrlLintItem(File resourceFile, String fhirReference)
    {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_URL_MISSING,
                "<url> element is missing"
        );
    }
}
