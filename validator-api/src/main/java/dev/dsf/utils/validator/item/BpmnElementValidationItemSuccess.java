package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import java.io.File;

/**
 * Represents a validation item for a successful or "no-issue" BPMN validation result.
 * <p>
 * This class extends {@link BpmnElementValidationItem} and fixes its
 * {@link ValidationSeverity} to {@link ValidationSeverity#SUCCESS}.
 * </p>
 */
public class BpmnElementValidationItemSuccess extends BpmnElementValidationItem
{
    /**
     * Constructs a new success validation item with the given parameters.
     *
     * @param elementId   the BPMN element ID (e.g., StartEvent_1)
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the BPMN process definition ID or key
     * @param description a short message describing the successful validation result
     */
    public BpmnElementValidationItemSuccess(
            String elementId,
            File bpmnFile,
            String processId,
            String description)
    {
        super(ValidationSeverity.SUCCESS, elementId, bpmnFile, processId, description);
    }

    /**
     * Returns the description set by the constructor.
     *
     * @return the success description for this validation item
     */
    @Override
    public String getDescription()
    {
        return description;
    }
    @Override
    public String toString()
    {
        return String.format("[%s] %s (elementId=%s, processId=%s, file=%s, description=%s)",
                ValidationSeverity.SUCCESS,
                this.getClass().getSimpleName(),
                elementId,
                processId,
                bpmnFile.getName(),
                description);
    }
}
