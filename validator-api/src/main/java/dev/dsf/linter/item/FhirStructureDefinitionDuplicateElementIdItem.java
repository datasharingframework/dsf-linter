package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that duplicate element {@code id} values were found
 * in the StructureDefinition differential section.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_ELEMENT_ID_DUPLICATE}.</p>
 */
public class FhirStructureDefinitionDuplicateElementIdItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionDuplicateElementIdItem(File resourceFile, String fhirReference, String id)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_ELEMENT_ID_DUPLICATE,
                "Duplicate element @id found: '" + id + "'"
        );
    }
}
