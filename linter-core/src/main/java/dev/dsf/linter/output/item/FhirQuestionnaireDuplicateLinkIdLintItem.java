package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that a {@code linkId} in a Questionnaire
 * is not unique across {@code <item>} elements.
 *
 * <p>According to the FHIR and DSF specifications, each {@code <item>} in a Questionnaire
 * must have a unique {@code linkId}. Duplicate values result in ambiguity during
 * QuestionnaireResponse mapping and user interaction.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_DUPLICATE_LINK_ID}
 * and is reported with {@link LinterSeverity#WARN}.</p>
 */
public class FhirQuestionnaireDuplicateLinkIdLintItem extends FhirElementLintItem {
    /**
     * Constructs a Lint Item using the standard message for duplicate linkId values.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param linkId        the duplicated linkId
     */
    public FhirQuestionnaireDuplicateLinkIdLintItem(File resourceFile,
                                                    String fhirReference,
                                                    String linkId) {
        this(resourceFile, fhirReference,
                "Duplicate Questionnaire item linkId detected: '" + linkId + "'", false);
    }

    /**
     * Constructs a Lint Item with a custom message for duplicate linkId values.
     *
     * @param resourceFile  the file containing the Questionnaire
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   the message describing the issue
     * @param custom        whether this is a custom message
     */
    public FhirQuestionnaireDuplicateLinkIdLintItem(File resourceFile,
                                                    String fhirReference,
                                                    String description,
                                                    boolean custom) {
        super(
                LinterSeverity.WARN,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_DUPLICATE_LINK_ID,
                description
        );
    }
}
