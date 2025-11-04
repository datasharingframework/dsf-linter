package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a CodeSystem resource does not contain
 * any {@code <concept>} elements.
 *
 * <p>According to the DSF CodeSystem base profile, at least one {@code concept}
 * entry may is defined the set of codes provided by the system.</p>
 *
 * <p>This issue corresponds to {@link LintingType#CODE_SYSTEM_MISSING_CONCEPT}.</p>
 */
public class FhirCodeSystemMissingConceptLintItem extends FhirElementLintItem {
    /**
     * Constructs a new Lint Item with the default message indicating the missing concept list.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical or local reference of the CodeSystem
     */
    public FhirCodeSystemMissingConceptLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "CodeSystem is missing required <concept> elements.", false);
    }

    /**
     * Constructs a new Lint Item with a custom message.
     *
     * @param resourceFile  the file containing the CodeSystem resource
     * @param fhirReference the canonical or local reference of the CodeSystem
     * @param description   custom error description
     * @param custom        whether the message is custom
     */
    public FhirCodeSystemMissingConceptLintItem(File resourceFile,
                                                String fhirReference,
                                                String description,
                                                boolean custom) {
        super(LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.CODE_SYSTEM_MISSING_CONCEPT,
                description);
    }
}
