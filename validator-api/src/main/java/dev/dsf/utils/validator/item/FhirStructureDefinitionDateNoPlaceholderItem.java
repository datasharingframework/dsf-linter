package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating the {@code date} element is missing
 * the {@code #{date}} placeholder required by DSF conventions.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_DATE_NO_PLACEHOLDER}.</p>
 */
public class FhirStructureDefinitionDateNoPlaceholderItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionDateNoPlaceholderItem(File resourceFile, String fhirReference, String actual)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_DATE_NO_PLACEHOLDER,
                "StructureDefinition <date> must contain '#{date}', but was: " + actual
        );
    }
}
