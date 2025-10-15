package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code meta.profile} element is missing
 * in a Questionnaire resource.
 *
 * <p>This check is required by the DSF Questionnaire profile to ensure that
 * the resource explicitly declares conformance to the expected DSF structure definition.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_MISSING_META_PROFILE}.</p>
 */
public class FhirQuestionnaireMissingMetaProfileValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a new validation item with the default message for a missing {@code meta.profile}.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireMissingMetaProfileValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Questionnaire <meta.profile> is missing.", false);
    }

    /**
     * Constructs a new validation item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom validation message describing the issue
     * @param custom        whether the message is a custom string
     */
    public FhirQuestionnaireMissingMetaProfileValidationItem(File resourceFile,
                                                             String fhirReference,
                                                             String description,
                                                             boolean custom)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_MISSING_META_PROFILE,
                description
        );
    }
}
