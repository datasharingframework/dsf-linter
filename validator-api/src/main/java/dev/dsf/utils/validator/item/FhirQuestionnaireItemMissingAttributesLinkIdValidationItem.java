package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a Questionnaire {@code <item>} element
 * is missing one required attribute: {@code linkId}.
 *
 * <p>Each item in a DSF Questionnaire must define these attributes to ensure proper
 * rendering and semantic interpretation during user interaction. Missing attributes
 * result in an invalid or unusable form.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_LINK_ID}
 * and is reported with {@link ValidationSeverity#ERROR}.</p>
 */
public class FhirQuestionnaireItemMissingAttributesLinkIdValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item with the default message indicating attribute incompleteness.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireItemMissingAttributesLinkIdValidationItem(File resourceFile, String fhirReference)
    {
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
    public FhirQuestionnaireItemMissingAttributesLinkIdValidationItem(File resourceFile,
                                                                String fhirReference,
                                                                String description,
                                                                boolean custom)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_ITEM_MISSING_ATTRIBUTES_LINK_ID,
                description
        );
    }
}
