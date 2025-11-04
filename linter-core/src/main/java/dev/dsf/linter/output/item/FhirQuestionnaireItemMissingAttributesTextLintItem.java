package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a Questionnaire {@code <item>} element
 * is missing the optional but recommended {@code text} attribute.
 *
 * <p>Although the {@code text} attribute is not strictly required, its presence
 * improves questionnaire readability and usability during form filling.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TEXT}
 * and is reported with {@link LinterSeverity#INFO}.</p>
 */
public class FhirQuestionnaireItemMissingAttributesTextLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item with the default message for missing {@code text} attribute.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireItemMissingAttributesTextLintItem(File resourceFile, String fhirReference) {
        super(
                LinterSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TEXT,
                "Questionnaire item is missing optional attribute: text."
        );
    }

    /**
     * Constructs a Lint Item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   the message describing the issue
     * @param custom        whether the message was custom defined
     */
    public FhirQuestionnaireItemMissingAttributesTextLintItem(File resourceFile,
                                                              String fhirReference,
                                                              String description,
                                                              boolean custom) {
        super(
                LinterSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TEXT,
                description
        );
    }
}
