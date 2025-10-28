package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating that the {@code status} element is not set to {@code unknown}.
 *
 * <p>Corresponds to {@link LintingType#STRUCTURE_DEFINITION_INVALID_STATUS}.</p>
 */
public class FhirStructureDefinitionInvalidStatusLintItem extends FhirElementLintItem
{
    public FhirStructureDefinitionInvalidStatusLintItem(File resourceFile, String fhirReference, String actual)
    {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_INVALID_STATUS,
                "Status must be 'unknown', but was: '" + actual + "'"
        );
    }
}
