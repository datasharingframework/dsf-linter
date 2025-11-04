package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the {@code StructureDefinition} profile referenced in a {@code Task}
 * could not be located, which prevents cardinality validation.
 *
 * <p>This Lint issue corresponds to {@link LintingType#Fhir_TASK_PROFILE_NOT_FOUND}.</p>
 */
public class FhirTaskCouldNotLoadProfileLintItem extends FhirElementLintItem {
    public FhirTaskCouldNotLoadProfileLintItem(File resourceFile, String fhirReference, String description) {
        super(LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.Fhir_TASK_PROFILE_NOT_FOUND,
                description != null ? description : "Referenced StructureDefinition could not be loaded.");
    }
}
