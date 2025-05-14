package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a {@code linkId} in a Questionnaire
 * is not unique across {@code <item>} elements.
 *
 * <p>According to the FHIR and DSF specifications, each {@code <item>} in a Questionnaire
 * must have a unique {@code linkId}. Duplicate values result in ambiguity during
 * QuestionnaireResponse mapping and user interaction.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_DUPLICATE_LINK_ID}
 * and is reported with {@link ValidationSeverity#WARN}.</p>
 */
public class FhirQuestionnaireDuplicateLinkIdValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item using the standard message for duplicate linkId values.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param linkId        the duplicated linkId
     */
    public FhirQuestionnaireDuplicateLinkIdValidationItem(File resourceFile,
                                                          String fhirReference,
                                                          String linkId)
    {
        this(resourceFile, fhirReference,
                "Duplicate Questionnaire item linkId detected: '" + linkId + "'", false);
    }

    /**
     * Constructs a validation item with a custom message for duplicate linkId values.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   the message describing the issue
     * @param custom        whether this is a custom message
     */
    public FhirQuestionnaireDuplicateLinkIdValidationItem(File resourceFile,
                                                          String fhirReference,
                                                          String description,
                                                          boolean custom)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_DUPLICATE_LINK_ID,
                description
        );
    }
}
