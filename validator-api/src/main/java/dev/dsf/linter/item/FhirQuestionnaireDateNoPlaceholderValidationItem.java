package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code date} element in a Questionnaire
 * does not contain the required {@code #{date}} placeholder.
 *
 * <p>The DSF Questionnaire template requires the use of the {@code #{date}} placeholder
 * in the {@code <date>} field. The actual timestamp is dynamically injected by the
 * BPE during deployment, so the static XML file must retain the unresolved placeholder.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_DATE_NO_PLACEHOLDER}.</p>
 */
public class FhirQuestionnaireDateNoPlaceholderValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item with the standard error message
     * for a missing {@code #{date}} placeholder.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireDateNoPlaceholderValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Questionnaire <date> must contain the placeholder '#{date}'.", false);
    }

    /**
     * Constructs a validation item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom validation message
     * @param custom        whether the message was manually defined
     */
    public FhirQuestionnaireDateNoPlaceholderValidationItem(File resourceFile,
                                                            String fhirReference,
                                                            String description,
                                                            boolean custom)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_DATE_NO_PLACEHOLDER,
                description
        );
    }
}
