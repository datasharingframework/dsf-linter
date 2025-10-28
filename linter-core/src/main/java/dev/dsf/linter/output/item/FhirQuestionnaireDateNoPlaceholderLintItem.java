package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that the {@code date} element in a Questionnaire
 * does not contain the required {@code #{date}} placeholder.
 *
 * <p>The DSF Questionnaire template requires the use of the {@code #{date}} placeholder
 * in the {@code <date>} field. The actual timestamp is dynamically injected by the
 * BPE during deployment, so the static XML file must retain the unresolved placeholder.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_DATE_NO_PLACEHOLDER}.</p>
 */
public class FhirQuestionnaireDateNoPlaceholderLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item with the standard error message
     * for a missing {@code #{date}} placeholder.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireDateNoPlaceholderLintItem(File resourceFile, String fhirReference) {
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
    public FhirQuestionnaireDateNoPlaceholderLintItem(File resourceFile,
                                                      String fhirReference,
                                                      String description,
                                                      boolean custom) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_DATE_NO_PLACEHOLDER,
                description
        );
    }
}
