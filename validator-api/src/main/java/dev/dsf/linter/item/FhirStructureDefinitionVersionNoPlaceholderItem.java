package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating the {@code version} element is missing
 * the {@code #{version}} placeholder required by DSF conventions.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_VERSION_NO_PLACEHOLDER}.</p>
 */
public class FhirStructureDefinitionVersionNoPlaceholderItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionVersionNoPlaceholderItem(File resourceFile, String fhirReference, String actual)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_VERSION_NO_PLACEHOLDER,
                "StructureDefinition <version> must contain '#{version}', but was: " + actual
        );
    }
}
