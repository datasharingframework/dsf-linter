package dev.dsf.linter.output.item;

import dev.dsf.linter.output.ValidationSeverity;

/**
 * <p>
 * A validation item tied to a specific BPMN element, storing the short BPMN filename
 * and the process/element IDs.
 * </p>
 */
public abstract class BpmnElementValidationItem extends BpmnValidationItem
{
    protected final String elementId;
    protected final String processId;
    protected final String description;

    /**
     * Constructs a {@code BpmnElementValidationItem}.
     *
     * @param severity        the validation severity (ERROR, WARN, SUCCESS, etc.)
     * @param elementId       e.g., "StartEvent_1"
     * @param bpmnFileName    short BPMN file name, e.g., "download-allow-list.bpmn"
     * @param processId       e.g., "dsfdev_downloadAllowList"
     * @param description     textual description of the validation issue
     */
    public BpmnElementValidationItem(ValidationSeverity severity,
                                     String elementId,
                                     String bpmnFileName,
                                     String processId,
                                     String description)
    {
        // We pass short filename to the parent constructor
        super(severity, bpmnFileName);
        this.elementId = elementId;
        this.processId = processId;
        this.description = description;
    }

    public String getElementId()
    {
        return elementId;
    }

    public String getProcessId()
    {
        return processId;
    }

    public abstract String getDescription();

    @Override
    public String toString()
    {
        return String.format(
                "[%s] %s (elementId=%s, processId=%s, file=%s) : %s",
                getSeverity(),
                this.getClass().getSimpleName(),
                elementId,
                processId,
                getBpmnFile(),
                description
        );
    }
}
