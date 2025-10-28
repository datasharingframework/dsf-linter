package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a mandatory Questionnaire item
 * (e.g., {@code business-key} or {@code user-task-id}) has an invalid {@code type} attribute.
 *
 * <p>According to DSF specification, both items must be defined with <code>type="string"</code>.
 * Any deviation from this results in a structurally invalid DSF Questionnaire resource.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_MANDATORY_ITEM_INVALID_TYPE}
 * and is reported as a {@link ValidationSeverity#ERROR}.</p>
 */
public class FhirQuestionnaireMandatoryItemInvalidTypeValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item with a standardized message indicating that
     * the item has an incorrect {@code type} value.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param linkId        the linkId of the affected mandatory item
     * @param actualType    the value of the {@code type} attribute found in the item
     */
    public FhirQuestionnaireMandatoryItemInvalidTypeValidationItem(File resourceFile,
                                                                   String fhirReference,
                                                                   String linkId,
                                                                   String actualType)
    {
        this(resourceFile, fhirReference,
                "Mandatory item '" + linkId + "' must have type='string' (found: '" + actualType + "').",
                false);
    }

    /**
     * Constructs a validation item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom validation message
     * @param custom        whether the message is custom-defined
     */
    public FhirQuestionnaireMandatoryItemInvalidTypeValidationItem(File resourceFile,
                                                                   String fhirReference,
                                                                   String description,
                                                                   boolean custom)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_MANDATORY_ITEM_INVALID_TYPE,
                description
        );
    }
}
