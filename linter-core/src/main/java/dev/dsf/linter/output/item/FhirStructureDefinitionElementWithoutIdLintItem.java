package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting item indicating that an element in the differential section lacks a required {@code id} attribute.
 *
 * <p>Corresponds to {@link LintingType#STRUCTURE_DEFINITION_ELEMENT_ID_MISSING}.</p>
 */
public class FhirStructureDefinitionElementWithoutIdLintItem extends FhirElementLintItem
{
    public FhirStructureDefinitionElementWithoutIdLintItem(File resourceFile, String fhirReference)
    {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_ELEMENT_ID_MISSING,
                "Element in <differential> is missing required @id attribute"
        );
    }
}
