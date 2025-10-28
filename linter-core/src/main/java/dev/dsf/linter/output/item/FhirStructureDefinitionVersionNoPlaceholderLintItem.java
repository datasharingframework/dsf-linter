package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating the {@code version} element is missing
 * the {@code #{version}} placeholder required by DSF conventions.
 *
 * <p>Corresponds to {@link LintingType#STRUCTURE_DEFINITION_VERSION_NO_PLACEHOLDER}.</p>
 */
public class FhirStructureDefinitionVersionNoPlaceholderLintItem extends FhirElementLintItem
{
    public FhirStructureDefinitionVersionNoPlaceholderLintItem(File resourceFile, String fhirReference, String actual)
    {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_VERSION_NO_PLACEHOLDER,
                "StructureDefinition <version> must contain '#{version}', but was: " + actual
        );
    }
}
