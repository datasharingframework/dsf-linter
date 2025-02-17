package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;

/**
 * Validation item indicating that a required process ID is missing
 * (for example, a Start Event or End Event outside a SubProcess).
 * Corresponds to {@link ValidationType#BPMN_PROCESS_ID_MISSING}.
 */
public class BpmnProcessIdMissingValidationItem extends BpmnElementValidationItem
{
    /**
     * Constructs a BpmnProcessIdMissingValidationItem.
     *
     * @param elementId the BPMN element ID (e.g. of the StartEvent or EndEvent)
     * @param bpmnFile  the BPMN file where the issue was found
     * @param processId the process definition or key (if any)
     */
    public BpmnProcessIdMissingValidationItem(String elementId, File bpmnFile, String processId)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
    }

}
