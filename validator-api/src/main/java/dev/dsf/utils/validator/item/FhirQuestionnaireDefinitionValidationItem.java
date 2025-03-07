package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;

/**
 * <p>
 * Represents a BPMN validation issue specifically related to FHIR QuestionnaireDefinition.
 * This class extends {@link BpmnElementValidationItem} so that it can be stored in a
 * {@code List<BpmnElementValidationItem>} while carrying FHIR-specific info.
 * </p>
 */
public class FhirQuestionnaireDefinitionValidationItem extends BpmnElementValidationItem
{
    private final String questionnaireDefinitionUrl;
    private final String details;

    public FhirQuestionnaireDefinitionValidationItem(
            ValidationSeverity severity,
            String elementId,
            File bpmnFile,
            String processId,
            String questionnaireDefinitionUrl,
            String details)
    {
        super(severity, elementId, bpmnFile, processId);
        this.questionnaireDefinitionUrl = questionnaireDefinitionUrl;
        this.details = details;
    }

    public String getQuestionnaireDefinitionUrl()
    {
        return questionnaireDefinitionUrl;
    }

    public String getDetails()
    {
        return details;
    }

    @Override
    public String getDescription()
    {
        return String.format(
                "QuestionnaireDefinition issue for '%s': %s",
                questionnaireDefinitionUrl,
                details
        );
    }
}
