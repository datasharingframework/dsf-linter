package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;

import java.io.File;

/**
 * Lint Item indicating that the {@code practitionerRole} input parameter in a task listener
 * has no value or is null/empty.
 * <p>
 * This lint item is created when an API v2 task listener defines a {@code practitionerRole}
 * input parameter (via {@code <camunda:inputParameter name="practitionerRole">}) within its
 * {@code extensionElements}, but the value is missing, null, or empty.
 * </p>
 * <p>
 * <strong>Validation Scope:</strong>
 * This validation applies to all API v2 task listeners (not just those extending
 * {@code DefaultUserTaskListener}). The validation only runs if the parameter is explicitly
 * defined in the BPMN file; if the parameter is not present, no lint items are generated.
 * </p>
 * <p>
 * <strong>Severity Levels:</strong>
 * The severity is determined based on whether the task listener extends
 * {@code dev.dsf.bpe.v2.activity.DefaultUserTaskListener}:
 * </p>
 * <ul>
 *   <li><strong>ERROR:</strong> When the task listener extends {@code DefaultUserTaskListener}
 *       (required parameter must have a value)</li>
 *   <li><strong>WARN:</strong> When the task listener does not extend {@code DefaultUserTaskListener}
 *       (parameter should have a value but is not strictly required)</li>
 * </ul>
 * <p>
 * <strong>Supported Value Formats:</strong>
 * The validator supports various value formats, including:
 * </p>
 * <ul>
 *   <li>Direct text content within the {@code inputParameter} element</li>
 *   <li>Nested {@code <camunda:string>} elements</li>
 *   <li>Nested {@code <camunda:list>} elements with {@code <camunda:value>} children</li>
 * </ul>
 * <p>
 * All formats are checked to ensure they contain non-empty values. If the value is null,
 * empty, or contains only whitespace, this lint item is created.
 * </p>
 */
public class BpmnPractitionerRoleHasNoValueOrNullLintItem extends BpmnElementLintItem {
    /**
     * Constructs a new Lint Item for an empty or null practitionerRole input parameter.
     *
     * @param severity   the severity level (ERROR for DefaultUserTaskListener, WARN for others)
     * @param elementId  the BPMN element ID
     * @param bpmnFile   the BPMN file being validated
     * @param processId  the process definition ID or key
     */
    public BpmnPractitionerRoleHasNoValueOrNullLintItem(LinterSeverity severity, String elementId, File bpmnFile, String processId) {
        super(severity, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId,
                "practitionerRole input parameter in task listener has no value or is null/empty");
    }

    /**
     * Constructs a new Lint Item with a custom description.
     *
     * @param severity    the severity level (ERROR for DefaultUserTaskListener, WARN for others)
     * @param elementId   the BPMN element ID
     * @param bpmnFile    the BPMN file being validated
     * @param processId   the process definition ID or key
     * @param description the custom lint description
     */
    public BpmnPractitionerRoleHasNoValueOrNullLintItem(LinterSeverity severity, String elementId, File bpmnFile, String processId, String description) {
        super(severity, elementId, bpmnFile != null ? bpmnFile.getName() : "unknown.bpmn", processId, description);
    }

    @Override
    public String getDescription() {
        return description;
    }
}

