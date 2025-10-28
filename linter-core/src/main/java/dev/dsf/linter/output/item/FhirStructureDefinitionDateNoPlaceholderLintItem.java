package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating the {@code date} element is missing
 * the {@code #{date}} placeholder required by DSF conventions.
 *
 * <p>Corresponds to {@link LintingType#STRUCTURE_DEFINITION_DATE_NO_PLACEHOLDER}.</p>
 */
public class FhirStructureDefinitionDateNoPlaceholderLintItem extends FhirElementLintItem
{
    public FhirStructureDefinitionDateNoPlaceholderLintItem(File resourceFile, String fhirReference, String actual)
    {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_DATE_NO_PLACEHOLDER,
                "StructureDefinition <date> must contain '#{date}', but was: " + actual
        );
    }
}
