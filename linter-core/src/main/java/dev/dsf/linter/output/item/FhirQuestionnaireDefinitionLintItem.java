package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * <p>
 * Represents a BPMN Lint issue specifically related to FHIR QuestionnaireDefinition.
 * This class extends {@link BpmnElementLintItem} so that it can be stored in a
 * {@code List<BpmnElementLintItem>} while carrying FHIR-specific info.
 * </p>
 */
public class FhirQuestionnaireDefinitionLintItem extends BpmnElementLintItem {
    private final String questionnaireDefinitionUrl;
    private final String details;

    public FhirQuestionnaireDefinitionLintItem(
            LinterSeverity severity,
            String elementId,
            File bpmnFile,
            String processId,
            String questionnaireDefinitionUrl,
            String details) {
        super(severity, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId,
                String.format("QuestionnaireDefinition issue for '%s': %s", questionnaireDefinitionUrl, details));
        this.questionnaireDefinitionUrl = questionnaireDefinitionUrl;
        this.details = details;
    }

    public String getQuestionnaireDefinitionUrl() {
        return questionnaireDefinitionUrl;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String getDescription() {
        return String.format(
                "QuestionnaireDefinition issue for '%s': %s",
                questionnaireDefinitionUrl,
                details
        );
    }
}
