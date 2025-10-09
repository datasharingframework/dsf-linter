package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.FloatingElementType;
import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;


public class BpmnFloatingElementValidationItem extends BpmnElementValidationItem
{
    private final ValidationType validationTypeOverride;
    private final FloatingElementType floatingElementType;

    /**
     * Constructs a floating-element validation item with a custom severity and a specific {@link FloatingElementType}.
     *
     * @param elementId              the BPMN element ID that caused the validation issue
     * @param bpmnFile               the BPMN file being validated
     * @param processId              the ID or key of the process definition where the issue occurred
     * @param description            a human-readable message describing the validation issue
     * @param validationTypeOverride the validation type to associate with this issue
     * @param severityOverride       the severity to assign to this validation issue
     * @param floatingElementType    the specific floating element type that categorizes this issue
     */
    public BpmnFloatingElementValidationItem(
            String elementId,
            File bpmnFile,
            String processId,
            String description,
            ValidationType validationTypeOverride,
            ValidationSeverity severityOverride,
            FloatingElementType floatingElementType)
    {
        super(severityOverride, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
        this.validationTypeOverride = validationTypeOverride;
        this.floatingElementType = floatingElementType;
    }

    /**
     * @return the human-readable description or reason for this floating-element validation
     */
    @Override
    public String getDescription()
    {
        return description;
    }

    /**
     * Gets the floating element type that categorizes this validation issue, if available.
     *
     * @return the {@link FloatingElementType}, or {@code null} if not specified
     */
    public FloatingElementType getFloatingElementType()
    {
        return floatingElementType;
    }

    /**
     * Returns a string representation of this validation item including element ID, process ID, BPMN file,
     * description, validation type override, floating element type (if any), and severity.
     *
     * @return a string with detailed information about this validation item
     */
    @Override
    public String toString()
    {
        String details = String.format(
                "description='%s', validationTypeOverride=%s, floatingElementType=%s",
                description,
                validationTypeOverride,
                floatingElementType
        );

        return String.format(
                "[%s] %s (elementId=%s, processId=%s, file=%s) : %s",
                getSeverity(),
                this.getClass().getSimpleName(),
                getElementId(),
                getProcessId(),
                getBpmnFile(),
                details
        );
    }
}
