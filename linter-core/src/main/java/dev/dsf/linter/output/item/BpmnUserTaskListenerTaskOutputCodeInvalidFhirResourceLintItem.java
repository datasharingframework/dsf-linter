package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Lint Item indicating that the taskOutputCode field value does not correspond to a valid code in the referenced CodeSystem.
 * <p>
 * The taskOutputCode should be a valid code within the CodeSystem referenced by taskOutputSystem.
 * </p>
 */
public class BpmnUserTaskListenerTaskOutputCodeInvalidFhirResourceLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new Lint Item for an invalid taskOutputCode FHIR resource.
     *
     * @param elementId the BPMN element ID of the {@code UserTask}
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param code      the code value that was found
     * @param system    the system URL that was referenced
     */
    public BpmnUserTaskListenerTaskOutputCodeInvalidFhirResourceLintItem(String elementId, File bpmnFile, String processId, String code, String system) {
        super(LinterSeverity.ERROR,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "taskOutputCode '" + code + "' is not a valid code in CodeSystem '" + system + "'");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

