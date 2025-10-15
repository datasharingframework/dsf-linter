package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a mandatory DSF Questionnaire item
 * (e.g., {@code business-key} or {@code user-task-id}) is not marked as {@code required="true"}.
 *
 * <p>These mandatory items must always include the {@code required="true"} attribute
 * to ensure they are enforced during user input.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_MANDATORY_ITEM_NOT_REQUIRED}
 * and is reported with {@link ValidationSeverity#ERROR}.</p>
 */
public class FhirQuestionnaireMandatoryItemNotRequiredValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item with a standard error message when a mandatory item
     * is not explicitly marked as required.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param linkId        the identifier of the affected item
     */
    public FhirQuestionnaireMandatoryItemNotRequiredValidationItem(File resourceFile,
                                                                   String fhirReference,
                                                                   String linkId)
    {
        this(resourceFile, fhirReference,
                "Mandatory item '" + linkId + "' must include required=\"true\".",
                false);
    }

    /**
     * Constructs a validation item with a custom error message.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom error description
     * @param custom        whether the message was manually specified
     */
    public FhirQuestionnaireMandatoryItemNotRequiredValidationItem(File resourceFile,
                                                                   String fhirReference,
                                                                   String description,
                                                                   boolean custom)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_MANDATORY_ITEM_NOT_REQUIRED,
                description
        );
    }
}
