package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code meta.profile} element is missing
 * in a FHIR StructureDefinition resource.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_META_PROFILE_MISSING}.</p>
 */
public class FhirStructureDefinitionMissingMetaProfileItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionMissingMetaProfileItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_META_PROFILE_MISSING,
                "meta.profile element is missing"
        );
    }
}
