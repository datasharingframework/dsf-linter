package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Corresponds to {@link ValidationType#BPMN_MESSAGE_START_EVENT_MESSAGE_NAME_EMPTY}.
 */
public class BpmnMessageStartEventMessageNameEmptyValidationItem extends BpmnElementValidationItem {
    public BpmnMessageStartEventMessageNameEmptyValidationItem(
            String elementId, File file, String processId) {
        super(ValidationSeverity.ERROR, elementId, file, processId);
    }

}
