package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that an element in the differential section lacks a required {@code id} attribute.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_ELEMENT_ID_MISSING}.</p>
 */
public class FhirStructureDefinitionElementWithoutIdItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionElementWithoutIdItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_ELEMENT_ID_MISSING,
                "Element in <differential> is missing required @id attribute"
        );
    }
}
