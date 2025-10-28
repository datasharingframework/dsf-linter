package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import java.io.File;

/**
 * <p>
 * Represents a validation item for a successful or "no-issue" BPMN validation result.
 * This class extends {@link BpmnElementLintItem} and uses a fixed
 * {@link LinterSeverity#SUCCESS}.
 * </p>
 *
 * <h3>Usage Example:</h3>
 * <pre>
 *   File fullPathFile = new File("C:/myProject/download-allow-list.bpmn");
 *   BpmnElementLintItemSuccess item = new BpmnElementLintItemSuccess(
 *       "StartEvent_1",        // elementId
 *       fullPathFile,          // pass the entire File object
 *       "dsfdev_downloadAllowList", // processId
 *       "ServiceTask has a non-empty name"
 *   );
 *   // This item now has severity=SUCCESS, and bpmnFile="download-allow-list.bpmn"
 * </pre>
 */
public class BpmnElementLintItemSuccess extends BpmnElementLintItem
{
    /**
     * Constructs a new success validation item with the given parameters.
     *
     * @param elementId   the BPMN element ID (e.g., "StartEvent_1")
     * @param bpmnFile    the BPMN file being validated (full path or short name)
     * @param processId   the BPMN process definition ID or key
     * @param description a short message describing the successful validation result
     */
    public BpmnElementLintItemSuccess(String elementId,
                                      File bpmnFile,
                                      String processId,
                                      String description)
    {
        // We pass only the short filename to the parent constructor:
        super(
                LinterSeverity.SUCCESS,
                elementId,
                (bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn"),
                processId,
                description
        );
    }

    /**
     * Returns the success description for this validation item.
     *
     * @return the description provided at construction
     */
    @Override
    public String getDescription()
    {
        return description;
    }

    /**
     * Returns a concise string representation. For example:
     * <pre>
     * [SUCCESS] BpmnElementLintItemSuccess
     *   (elementId=StartEvent_1, processId=dsfdev_downloadAllowList, file=download-allow-list.bpmn, description=ServiceTask has a name)
     * </pre>
     *
     * @return a formatted string summarizing this validation item
     */
    @Override
    public String toString()
    {
        return String.format(
                "[%s] %s (elementId=%s, processId=%s, file=%s, description=%s)",
                getSeverity(),
                this.getClass().getSimpleName(),
                elementId,
                processId,
                getBpmnFile(),
                description
        );
    }
}
