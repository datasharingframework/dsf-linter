package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a Questionnaire contains no {@code <item>} elements.
 *
 * <p>According to the DSF Questionnaire template, each Questionnaire should contain
 * one or more {@code <item>} elements defining the structure of the expected user input.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_MISSING_ITEM} and
 * is reported as a structural {@link LinterSeverity#ERROR}.</p>
 */
public class FhirQuestionnaireMissingItemLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item with the standard message indicating that no items were found.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireMissingItemLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Questionnaire contains no <item> elements.", false);
    }

    /**
     * Constructs a Lint Item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   the message describing the issue
     * @param custom        whether this message was custom defined
     */
    public FhirQuestionnaireMissingItemLintItem(File resourceFile,
                                                String fhirReference,
                                                String description,
                                                boolean custom) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_MISSING_ITEM,
                description
        );
    }
}
