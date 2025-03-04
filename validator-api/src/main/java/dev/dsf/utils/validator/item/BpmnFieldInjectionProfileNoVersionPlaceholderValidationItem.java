package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import java.io.File;

/**
 * Validation item indicating that the profile field injection does not contain a version placeholder.
 * Corresponds to {@link ValidationType#BPMN_FIELD_INJECTION_PROFILE_NO_VERSION_PLACEHOLDER}.
 */
public class BpmnFieldInjectionProfileNoVersionPlaceholderValidationItem extends BpmnElementValidationItem
{
    private final String description;
    private final String rawValue;

    /**
     * Constructs a new validation item for a profile field injection missing a version placeholder.
     *
     * @param elementId the BPMN element ID
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key
     * @param rawValue  the raw value from the field injection
     */
    public BpmnFieldInjectionProfileNoVersionPlaceholderValidationItem(String elementId, File bpmnFile, String processId, String rawValue)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.rawValue = rawValue;
        this.description = "Profile field injection does not contain a version placeholder: " + rawValue;
    }

    /**
     * Constructs a new validation item with a custom description.
     *
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param rawValue    the raw value from the field injection
     * @param description the custom validation description
     */
    public BpmnFieldInjectionProfileNoVersionPlaceholderValidationItem(String elementId, File bpmnFile, String processId, String rawValue, String description)
    {
        super(ValidationSeverity.WARN, elementId, bpmnFile, processId);
        this.rawValue = rawValue;
        this.description = description;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    public String getRawValue()
    {
        return rawValue;
    }
}
