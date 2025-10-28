package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that a Questionnaire {@code <item>} element
 * is missing one required attribute: {@code linkId}.
 *
 * <p>Each item in a DSF Questionnaire must define these attributes to ensure proper
 * rendering and semantic interpretation during user interaction. Missing attributes
 * result in an invalid or unusable form.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_LINK_ID}
 * and is reported with {@link LinterSeverity#ERROR}.</p>
 */
public class FhirQuestionnaireItemMissingAttributesLinkIdLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item with the default message indicating attribute incompleteness.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireItemMissingAttributesLinkIdLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Questionnaire item is missing required attribute(s): linkId or type.", false);
    }

    /**
     * Constructs a validation item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   the message describing the issue
     * @param custom        whether the message was custom defined
     */
    public FhirQuestionnaireItemMissingAttributesLinkIdLintItem(File resourceFile,
                                                                String fhirReference,
                                                                String description,
                                                                boolean custom) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_LINK_ID,
                description
        );
    }
}
