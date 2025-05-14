package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code url} element in a Questionnaire
 * does not match the expected value derived from the file name.
 *
 * <p>In the DSF, the {@code url} of a Questionnaire must follow the format:
 * {@code http://dsf.dev/fhir/Questionnaire/<file-name-without-extension>}.
 * This ensures that the canonical URL aligns with its physical representation.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_INVALID_URL}.</p>
 */
@Deprecated
public class FhirQuestionnaireInvalidUrlValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item with the standard message comparing actual and expected URL values.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param actualUrl     the URL found in the resource
     * @param expectedUrl   the expected DSF-compliant URL
     */
    public FhirQuestionnaireInvalidUrlValidationItem(File resourceFile,
                                                     String fhirReference,
                                                     String actualUrl,
                                                     String expectedUrl)
    {
        this(resourceFile, fhirReference,
                "Questionnaire <url> must be '" + expectedUrl + "' (found: '" + actualUrl + "').",
                false);
    }

    /**
     * Constructs a validation item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom validation message
     * @param custom        whether this is a custom message
     */
    public FhirQuestionnaireInvalidUrlValidationItem(File resourceFile,
                                                     String fhirReference,
                                                     String description,
                                                     boolean custom)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_INVALID_URL,
                description
        );
    }
}
