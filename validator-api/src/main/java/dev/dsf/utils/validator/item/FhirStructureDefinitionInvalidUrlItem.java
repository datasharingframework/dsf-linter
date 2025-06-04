package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code url} element does not start with the
 * required DSF prefix.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_URL_INFO}.</p>
 */
public class FhirStructureDefinitionInvalidUrlItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionInvalidUrlItem(File resourceFile, String fhirReference, String actual)
    {
        super(
                ValidationSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_URL_INFO,
                "<url>: '" + actual + "', does not start with 'http://dsf.dev/fhir/StructureDefinition/'"
        );
    }
}
