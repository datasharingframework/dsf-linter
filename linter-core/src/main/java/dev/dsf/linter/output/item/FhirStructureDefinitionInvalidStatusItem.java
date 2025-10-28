package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

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
