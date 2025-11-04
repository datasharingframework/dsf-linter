package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a mandatory Questionnaire item
 * (e.g., {@code business-key} or {@code user-task-id}) has an invalid {@code type} attribute.
 *
 * <p>According to DSF specification, both items must be defined with <code>type="string"</code>.
 * Any deviation from this results in a structurally invalid DSF Questionnaire resource.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_MANDATORY_ITEM_INVALID_TYPE}
 * and is reported as a {@link LinterSeverity#ERROR}.</p>
 */
public class FhirQuestionnaireMandatoryItemInvalidTypeLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item with a standardized message indicating that
     * the item has an incorrect {@code type} value.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param linkId        the linkId of the affected mandatory item
     * @param actualType    the value of the {@code type} attribute found in the item
     */
    public FhirQuestionnaireMandatoryItemInvalidTypeLintItem(File resourceFile,
                                                             String fhirReference,
                                                             String linkId,
                                                             String actualType) {
        this(resourceFile, fhirReference,
                "Mandatory item '" + linkId + "' must have type='string' (found: '" + actualType + "').",
                false);
    }

    /**
     * Constructs a Lint Item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom lint description
     * @param custom        whether the message is custom-defined
     */
    public FhirQuestionnaireMandatoryItemInvalidTypeLintItem(File resourceFile,
                                                             String fhirReference,
                                                             String description,
                                                             boolean custom) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_MANDATORY_ITEM_INVALID_TYPE,
                description
        );
    }
}
