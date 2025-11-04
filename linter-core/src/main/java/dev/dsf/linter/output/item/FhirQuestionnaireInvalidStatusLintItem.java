package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the {@code status} element of a Questionnaire
 * is not set to {@code unknown} as required by the DSF Questionnaire template.
 *
 * <p>While not a strict error, this is reported as {@link LinterSeverity#INFO}.
 * However, DSF authors are expected to leave the field at {@code unknown} in versioned resources.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_INVALID_STATUS}.</p>
 */
public class FhirQuestionnaireInvalidStatusLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item using the standard message and INFO severity.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param actualStatus  the status value found in the resource
     */
    public FhirQuestionnaireInvalidStatusLintItem(File resourceFile,
                                                  String fhirReference,
                                                  String actualStatus) {
        this(resourceFile, fhirReference,
                "Questionnaire <status> should be 'unknown' (found: '" + actualStatus + "').",
                false);
    }

    /**
     * Constructs a Lint Item with a custom message and INFO severity.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom lint description
     * @param custom        whether the message is custom-defined
     */
    public FhirQuestionnaireInvalidStatusLintItem(File resourceFile,
                                                  String fhirReference,
                                                  String description,
                                                  boolean custom) {
        super(
                LinterSeverity.INFO,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_INVALID_STATUS,
                description
        );
    }
}
