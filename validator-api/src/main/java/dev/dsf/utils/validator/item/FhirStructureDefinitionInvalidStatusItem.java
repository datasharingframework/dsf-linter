package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code status} element is not set to {@code unknown}.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_INVALID_STATUS}.</p>
 */
public class FhirStructureDefinitionInvalidStatusItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionInvalidStatusItem(File resourceFile, String fhirReference, String actual)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_INVALID_STATUS,
                "Status must be 'unknown', but was: '" + actual + "'"
        );
    }
}
