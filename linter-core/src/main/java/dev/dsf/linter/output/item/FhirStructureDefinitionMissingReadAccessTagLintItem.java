package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating that the required DSF read-access tag is missing
 * in the {@code meta.tag} of a StructureDefinition.
 *
 * <p>Corresponds to {@link LintingType#STRUCTURE_DEFINITION_READ_ACCESS_TAG_MISSING}.</p>
 */
public class FhirStructureDefinitionMissingReadAccessTagLintItem extends FhirElementLintItem
{
    public FhirStructureDefinitionMissingReadAccessTagLintItem(File resourceFile, String fhirReference)
    {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_READ_ACCESS_TAG_MISSING,
                "meta.tag must contain system='http://dsf.dev/fhir/CodeSystem/read-access-tag' and code='ALL'"
        );
    }
}
