package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code url} element is missing
 * from the StructureDefinition.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_URL_MISSING}.</p>
 */
public class FhirStructureDefinitionMissingUrlItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionMissingUrlItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_URL_MISSING,
                "<url> element is missing"
        );
    }
}
