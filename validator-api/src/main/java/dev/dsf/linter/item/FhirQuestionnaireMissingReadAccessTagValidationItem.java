package dev.dsf.linter.item;

import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.ValidationType;

import java.io.File;

/**
 * Validation item indicating that the {@code meta.tag} element for
 * {@code read-access-tag = ALL} is missing in a Questionnaire resource.
 *
 * <p>The DSF specification requires every Questionnaire to include a tag
 * in {@code meta.tag} with system {@code http://dsf.dev/fhir/CodeSystem/read-access-tag}
 * and code {@code ALL} to ensure proper access control across organizations.</p>
 *
 * <p>This issue corresponds to {@link ValidationType#QUESTIONNAIRE_MISSING_READ_ACCESS_TAG}.</p>
 */
public class FhirQuestionnaireMissingReadAccessTagValidationItem extends FhirElementValidationItem
{
    /**
     * Constructs a validation item using the standard message for a missing read-access tag.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireMissingReadAccessTagValidationItem(File resourceFile, String fhirReference)
    {
        this(resourceFile, fhirReference,
                "Questionnaire <meta.tag> with system 'http://dsf.dev/fhir/CodeSystem/read-access-tag' and code 'ALL' is missing.",
                false);
    }

    /**
     * Constructs a validation item with a custom description message.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom validation message
     * @param custom        whether this is a custom message
     */
    public FhirQuestionnaireMissingReadAccessTagValidationItem(File resourceFile,
                                                               String fhirReference,
                                                               String description,
                                                               boolean custom)
    {
        super(
                ValidationSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                ValidationType.QUESTIONNAIRE_MISSING_READ_ACCESS_TAG,
                description
        );
    }
}
