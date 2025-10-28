package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Linting warning indicating that a {@code snapshot} section is present in the StructureDefinition.
 * Snapshots are managed and injected by the BPE during deployment and should not be authored manually.
 *
 * <p>Corresponds to {@link LintingType#STRUCTURE_DEFINITION_SNAPSHOT_PRESENT}.</p>
 */
public class FhirStructureDefinitionSnapshotPresentLintItem extends FhirElementLintItem
{
    public FhirStructureDefinitionSnapshotPresentLintItem(File resourceFile, String fhirReference)
    {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.STRUCTURE_DEFINITION_SNAPSHOT_PRESENT,
                "Snapshot section is present but should be omitted (managed at runtime)"
        );
    }
}
