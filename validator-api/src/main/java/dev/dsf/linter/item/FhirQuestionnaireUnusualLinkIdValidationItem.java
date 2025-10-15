package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code linkId} attribute in a Questionnaire item
 * does not match the expected lowercase kebab-case pattern {@code [a-z0-9\\-]+}.
 *
 * <p>This is not a structural error but a convention used in DSF to promote consistency
 * and avoid ambiguity in {@code QuestionnaireResponse} mapping. This issue is reported as
 * {@link ValidationSeverity#WARN}.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_UNUSUAL_LINK_ID}.</p>
 */
public class FhirQuestionnaireUnusualLinkIdValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item with a standardized warning message for unusual {@code linkId} format.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param linkId        the non-conforming linkId
     */
    public FhirQuestionnaireUnusualLinkIdValidationItem(File resourceFile,
                                                        String fhirReference,
                                                        String linkId)
    {
        this(resourceFile, fhirReference,
                "Questionnaire item <linkId> '" + linkId + "' does not match the expected pattern [a-z0-9\\-]+.",
                false);
    }

    /**
     * Constructs a validation item with a custom warning message.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom message
     * @param custom        whether the message is manually defined
     */
    public FhirQuestionnaireUnusualLinkIdValidationItem(File resourceFile,
                                                        String fhirReference,
                                                        String description,
                                                        boolean custom)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_UNUSUAL_LINK_ID,
                description
        );
    }
}
