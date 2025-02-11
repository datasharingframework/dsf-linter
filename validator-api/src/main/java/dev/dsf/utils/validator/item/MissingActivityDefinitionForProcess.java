package dev.dsf.utils.validator.item;

import dev.dsf.utils.validator.ValidationSeverity;

public class MissingActivityDefinitionForProcess extends FhirValidationItem {
    private final String processId;

    public MissingActivityDefinitionForProcess(ValidationSeverity severity, String processId) {
        super(severity);
        this.processId = processId;
    }

    public String getProcessId() {
        return processId;
    }
}
