package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code meta.profile} value in a StructureDefinition
 * does not conform to the expected DSF URL format.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_META_PROFILE_INVALID}.</p>
 */
public class FhirStructureDefinitionInvalidMetaProfileItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionInvalidMetaProfileItem(File resourceFile, String fhirReference, String actual)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_META_PROFILE_INVALID,
                "Invalid meta.profile value: " + actual
        );
    }
}
