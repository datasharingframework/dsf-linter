package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a Questionnaire {@code <item>} element
 * is missing the optional but recommended {@code text} attribute.
 *
 * <p>Although the {@code text} attribute is not strictly required, its presence
 * improves questionnaire readability and usability during form filling.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TEXT}
 * and is reported with {@link ValidationSeverity#INFO}.</p>
 */
public class FhirQuestionnaireItemMissingAttributesTextValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item with the default message for missing {@code text} attribute.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireItemMissingAttributesTextValidationItem(File resourceFile, String fhirReference)
    {
        super(
                ValidationSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TEXT,
                "Questionnaire item is missing optional attribute: text."
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
    public FhirQuestionnaireItemMissingAttributesTextValidationItem(File resourceFile,
                                                                String fhirReference,
                                                                String description,
                                                                boolean custom)
    {
        super(
                ValidationSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_TEXT,
                description
        );
    }
}
