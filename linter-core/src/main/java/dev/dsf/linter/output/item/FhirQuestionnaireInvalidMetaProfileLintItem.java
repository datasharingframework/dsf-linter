package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Validation item indicating that the {@code meta.profile} element is present
 * but does not start with the required DSF Questionnaire profile URL.
 *
 * <p>The DSF specification requires the {@code meta.profile} to point to
 * {@code http://dsf.dev/fhir/StructureDefinition/questionnaire}. Any deviation
 * is considered invalid and must be corrected before deployment.</p>
 *
 * <p>This issue corresponds to {@link LintingType#QUESTIONNAIRE_INVALID_META_PROFILE}.</p>
 */
public class FhirQuestionnaireInvalidMetaProfileLintItem extends FhirElementLintItem {
    /**
     * Constructs a validation item using a standard message, showing the invalid value.
     *
     * @param resourceFile  the file containing the Questionnaire resource
     * @param fhirReference the canonical URL or local reference of the Questionnaire
     * @param actualProfile the profile URI that was found in the resource
     */
    public FhirQuestionnaireInvalidMetaProfileLintItem(File resourceFile,
                                                       String fhirReference,
                                                       String actualProfile) {
        this(resourceFile, fhirReference,
                "Questionnaire <meta.profile> must start with 'http://dsf.dev/fhir/StructureDefinition/questionnaire' (found: '" + actualProfile + "').",
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
    public FhirQuestionnaireInvalidMetaProfileLintItem(File resourceFile,
                                                       String fhirReference,
                                                       String description,
                                                       boolean custom) {
        super(
                LinterSeverity.ERROR,
                resourceFile != null ? resourceFile.getName() : "unknown.xml",
                fhirReference,
                LintingType.QUESTIONNAIRE_INVALID_META_PROFILE,
                description
        );
    }
}
