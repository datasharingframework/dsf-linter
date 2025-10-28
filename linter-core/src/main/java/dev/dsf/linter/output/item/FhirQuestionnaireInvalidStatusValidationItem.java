package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code status} element of a Questionnaire
 * is not set to {@code unknown} as required by the DSF Questionnaire template.
 *
 * <p>While not a strict error, this is reported as {@link ValidationSeverity#INFO}.
 * However, DSF authors are expected to leave the field at {@code unknown} in versioned resources.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_INVALID_STATUS}.</p>
 */
public class FhirQuestionnaireInvalidStatusValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item using the standard message and INFO severity.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param actualStatus  the status value found in the resource
     */
    public FhirQuestionnaireInvalidStatusValidationItem(File resourceFile,
                                                        String fhirReference,
                                                        String actualStatus)
    {
        this(resourceFile, fhirReference,
                "Questionnaire <status> should be 'unknown' (found: '" + actualStatus + "').",
                false);
    }

    /**
     * Constructs a validation item with a custom message and INFO severity.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom validation message
     * @param custom        whether the message is custom-defined
     */
    public FhirQuestionnaireInvalidStatusValidationItem(File resourceFile,
                                                        String fhirReference,
                                                        String description,
                                                        boolean custom)
    {
        super(
                ValidationSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_INVALID_STATUS,
                description
        );
    }
}
