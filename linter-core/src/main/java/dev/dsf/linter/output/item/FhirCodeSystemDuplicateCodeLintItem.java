package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a {@code <concept>} entry in a CodeSystem
 * contains a {@code <code>} value that is not unique.
 *
 * <p>According to the DSF CodeSystem base profile, all codes must be unique
 * within a single CodeSystem resource.</p>
 *
 * <p>This issue corresponds to {@link LintingType#CODE_SYSTEM_DUPLICATE_CODE}.</p>
 */
public class FhirCodeSystemDuplicateCodeLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item for a duplicated concept code.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical or local reference of the CodeSystem
     * @param duplicateCode the duplicate code that was found
     */
    public FhirCodeSystemDuplicateCodeLintItem(File resourceFile,
                                               String fhirReference,
                                               String duplicateCode) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.CODE_SYSTEM_DUPLICATE_CODE,
                "Duplicate <code> value in CodeSystem <concept>: '" + duplicateCode + "'");
    }
}
