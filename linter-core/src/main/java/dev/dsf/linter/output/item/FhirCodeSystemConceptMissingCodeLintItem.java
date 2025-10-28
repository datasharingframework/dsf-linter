package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a {@code concept} element in a CodeSystem resource
 * is missing the required {@code <code>} sub-element.
 *
 * <p>According to the DSF CodeSystem base profile, each concept must define a code
 * that uniquely identifies the entry.</p>
 *
 * <p>This issue corresponds to {@link LintingType#CODE_SYSTEM_CONCEPT_MISSING_CODE}.</p>
 */
public class FhirCodeSystemConceptMissingCodeLintItem extends FhirElementLintItem {
    /**
     * Constructs a new validation item using the default description.
     *
     * @param resourceFile  the file containing the CodeSystem
     * @param fhirReference the canonical or local reference of the CodeSystem
     */
    public FhirCodeSystemConceptMissingCodeLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "CodeSystem <concept> element is missing required <code>.", false);
    }

    /**
     * Constructs a new validation item with a custom message.
     *
     * @param resourceFile  the file containing the CodeSystem
     * @param fhirReference the canonical or local reference of the CodeSystem
     * @param description   a custom validation message
     * @param custom        whether the message is custom or not
     */
    public FhirCodeSystemConceptMissingCodeLintItem(File resourceFile,
                                                    String fhirReference,
                                                    String description,
                                                    boolean custom) {
        super(LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.CODE_SYSTEM_CONCEPT_MISSING_CODE,
                description);
    }
}
