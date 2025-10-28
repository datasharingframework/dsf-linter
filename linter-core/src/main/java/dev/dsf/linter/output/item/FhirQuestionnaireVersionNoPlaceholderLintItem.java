package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that the {@code version} element in a Questionnaire
 * does not contain the required {@code #{version}} placeholder.
 *
 * <p>This placeholder is required by the DSF template. The final version string
 * is filled in dynamically by the BPE at deployment time. The raw file must retain
 * the unresolved placeholder value.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_VERSION_NO_PLACEHOLDER}.</p>
 */
public class FhirQuestionnaireVersionNoPlaceholderLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item with a default message stating that the version
     * field must contain the {@code #{version}} placeholder.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     */
    public FhirQuestionnaireVersionNoPlaceholderLintItem(File resourceFile, String fhirReference) {
        this(resourceFile, fhirReference,
                "Questionnaire <version> must contain the placeholder '#{version}'.", false);
    }

    /**
     * Constructs a validation item with a custom error message.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param description   a custom validation message
     * @param custom        whether this is a custom message
     */
    public FhirQuestionnaireVersionNoPlaceholderLintItem(File resourceFile,
                                                         String fhirReference,
                                                         String description,
                                                         boolean custom) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_VERSION_NO_PLACEHOLDER,
                description
        );
    }
}
