package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the required DSF read-access tag is missing
 * in the {@code meta.tag} of a StructureDefinition.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_READ_ACCESS_TAG_MISSING}.</p>
 */
public class FhirStructureDefinitionMissingReadAccessTagItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionMissingReadAccessTagItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_READ_ACCESS_TAG_MISSING,
                "meta.tag must contain system='http://dsf.dev/fhir/CodeSystem/read-access-tag' and code='ALL'"
        );
    }
}
