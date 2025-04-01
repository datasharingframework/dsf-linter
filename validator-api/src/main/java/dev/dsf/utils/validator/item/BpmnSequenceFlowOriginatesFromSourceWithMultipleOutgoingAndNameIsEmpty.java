package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import java.io.File;

/**
 * Validation item indicating that a sequence flow originates from a NON-FLOATING
 * source with multiple outgoing flows and has an empty name.
 * <p>
 * Corresponds to a BPMN validation scenario where the flow's source node is not
 * floating, has more than one outgoing sequence flow, and the sequence flow's name is empty.
 * </p>
 */
public class BpmnSequenceFlowOriginatesFromSourceWithMultipleOutgoingAndNameIsEmpty
        extends BpmnElementValidationItem
{
    /**
     * Constructs a new validation item indicating that a sequence flow from a
     * NON-FLOATING source with multiple outgoing flows has an empty name.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     */
    public BpmnSequenceFlowOriginatesFromSourceWithMultipleOutgoingAndNameIsEmpty(String elementId,
                                                                                 File bpmnFile,
                                                                                 String processId)
    {
        super(ValidationSeverity.WARN,
                elementId,
                bpmnFile,
                processId,
                "Sequence flow originates from a NON-FLOATING source with multiple outgoing flows and name is empty.");
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom validation description
     */
    public BpmnSequenceFlowOriginatesFromSourceWithMultipleOutgoingAndNameIsEmpty(String elementId,
                                                                                 File bpmnFile,
                                                                                 String processId,
                                                                                 String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId, description);
    }

    @Override
    public String getDescription()
    {
        return description;
    }
}
