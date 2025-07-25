package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a Questionnaire contains no {@code <item>} elements.
 *
 * <p>According to the DSF Questionnaire template, each Questionnaire should contain
 * one or more {@code <item>} elements defining the structure of the expected user input.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_MISSING_ITEM} and
 * is reported as a structural {@link ValidationSeverity#ERROR}.</p>
 */
public class FhirQuestionnaireMissingItemValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item with the standard message indicating that no items were found.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireMissingItemValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Questionnaire contains no <item> elements.", false);
    }

    /**
     * Constructs a validation item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   the message describing the issue
     * @param custom        whether this message was custom defined
     */
    public FhirQuestionnaireMissingItemValidationItem(File resourceFile,
                                                      String fhirReference,
                                                      String description,
                                                      boolean custom)
    {
        super(
                ValidationSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_MISSING_ITEM,
                description
        );
    }
}
