package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the {@code meta.profile} element is missing
 * in a Questionnaire resource.
 *
 * <p>This check is required by the DSF Questionnaire profile to ensure that
 * the resource explicitly declares conformance to the expected DSF structure definition.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_MISSING_META_PROFILE}.</p>
 */
public class FhirQuestionnaireMissingMetaProfileLintItem extends FhirElementLintItem {
    /**
     * Constructs a new Lint Item with the default message for a missing {@code meta.profile}.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireMissingMetaProfileLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Questionnaire <meta.profile> is missing.", false);
    }

    /**
     * Constructs a new Lint Item with a custom message.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom lint description describing the issue
     * @param custom        whether the message is a custom string
     */
    public FhirQuestionnaireMissingMetaProfileLintItem(File resourceFile,
                                                       String fhirReference,
                                                       String description,
                                                       boolean custom) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_MISSING_META_PROFILE,
                description
        );
    }
}
