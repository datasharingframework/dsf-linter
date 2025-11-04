package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

import java.io.File;

/**
 * Lint Item indicating that the implementation class for a message send event does not implement the JavaDelegate interface.
 * Corresponds to {@link LintingType#BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_IMPLEMENTING_JAVA_DELEGATE}.
 */
public class BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateLintItem extends BpmnElementLintItem {
    private final String className;

    /**
     * Constructs a new Lint Item for a message send event where the implementation class does not implement JavaDelegate.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param className the implementation class name that does not implement JavaDelegate
     */
    public BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateLintItem(String elementId, File bpmnFile, String processId, String className) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, "Message send event implementation class does not implement JavaDelegate: " + className);
        this.className = className;
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param className   the implementation class name that does not implement JavaDelegate
     * @param description the custom lint description
     */
    public BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateLintItem(String elementId, File bpmnFile, String processId, String className, String description) {
        super(LinterSeverity.ERROR, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
        this.className = className;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public String getClassName() {
        return className;
    }

    @Override
    public String toString() {
        return String.format(
                "%s, missingClassName=%s",
                super.toString(),
                className
        );
    }

}
