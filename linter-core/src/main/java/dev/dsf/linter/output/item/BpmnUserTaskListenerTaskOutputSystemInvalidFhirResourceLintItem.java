package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Lint Item indicating that the taskOutputSystem field value does not correspond to a valid CodeSystem URL.
 * <p>
 * The taskOutputSystem should reference a valid CodeSystem that exists in the project or its dependencies.
 * </p>
 */
public class BpmnUserTaskListenerTaskOutputSystemInvalidFhirResourceLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new Lint Item for an invalid taskOutputSystem FHIR resource.
     *
     * @param elementId the BPMN element ID of the {@code UserTask}
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param system    the system URL that was referenced
     */
    public BpmnUserTaskListenerTaskOutputSystemInvalidFhirResourceLintItem(String elementId, File bpmnFile, String processId, String system) {
        super(LinterSeverity.ERROR,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "taskOutputSystem '" + system + "' does not reference a valid CodeSystem");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

