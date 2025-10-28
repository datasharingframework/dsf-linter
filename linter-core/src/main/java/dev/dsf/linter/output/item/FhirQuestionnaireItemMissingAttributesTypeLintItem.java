package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a Questionnaire {@code <item>} element
 * is missing the required {@code type} attribute.
 *
 * <p>Every item in a DSF Questionnaire must declare a valid {@code type} in
 * accordance with the DSF profile, which ensures correct behavior during
 * questionnaire rendering and processing.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TYPE}
 * and is reported with {@link LinterSeverity#ERROR}.</p>
 */
public class FhirQuestionnaireItemMissingAttributesTypeLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item with the default error message for missing {@code type} attribute.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireItemMissingAttributesTypeLintItem(File resourceFile, String fhirReference) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TYPE,
                "Questionnaire item is missing required attribute: type."
        );
    }

    /**
     * Constructs a validation item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   the message describing the issue
     * @param custom        whether the message was custom defined
     */
    public FhirQuestionnaireItemMissingAttributesTypeLintItem(File resourceFile,
                                                              String fhirReference,
                                                              String description,
                                                              boolean custom) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TYPE,
                description
        );
    }
}
