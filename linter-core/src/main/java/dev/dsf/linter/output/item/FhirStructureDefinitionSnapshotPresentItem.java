package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation warning indicating that a {@code snapshot} section is present in the StructureDefinition.
 * Snapshots are managed and injected by the BPE during deployment and should not be authored manually.
 *
 * <p>Corresponds to {@link ValidationType#STRUCTURE_DEFINITION_SNAPSHOT_PRESENT}.</p>
 */
public class FhirStructureDefinitionSnapshotPresentItem extends FhirElementValidationItem
{
    public FhirStructureDefinitionSnapshotPresentItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.STRUCTURE_DEFINITION_SNAPSHOT_PRESENT,
                "Snapshot section is present but should be omitted (managed at runtime)"
        );
    }
}
