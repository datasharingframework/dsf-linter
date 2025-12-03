package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Lint Item indicating that the taskOutputVersion field value does not contain a placeholder.
 * <p>
 * The taskOutputVersion should contain a placeholder (e.g., #{version}) to be dynamically replaced
 * at runtime. Hardcoded version values are not wished.
 * </p>
 */
public class BpmnUserTaskListenerTaskOutputVersionNoPlaceholderLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new Lint Item for a taskOutputVersion that does not contain a placeholder.
     *
     * @param elementId the BPMN element ID of the {@code UserTask}
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param version   the version value that was found
     * @param system    the system URL that was referenced
     */
    public BpmnUserTaskListenerTaskOutputVersionNoPlaceholderLintItem(String elementId, File bpmnFile, String processId, String version, String system) {
        super(LinterSeverity.WARN,
                elementId,
                bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn",
                processId,
                "taskOutputVersion must contain a placeholder (e.g., #{version}), got: '" + version + "'");
    }

    @Override
    public String getDescription() {
        return description;
    }
}

