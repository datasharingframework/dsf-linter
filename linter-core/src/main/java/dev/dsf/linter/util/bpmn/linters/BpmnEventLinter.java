package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.ConditionalEventDefinition;
import org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Utility class for validating BPMN event definitions (error boundary events and conditional events).
 * <p>
 * This class provides validation methods to ensure that error boundary events and conditional
 * events are properly configured with required attributes and values.
 * </p>
 */
public final class BpmnEventLinter {

    private BpmnEventLinter() {
        // Utility class - no instantiation
    }

    /**
     * Lints a {@link BoundaryEvent} that contains an {@link ErrorEventDefinition}.
     * <p>
     * The linter is split based on whether an error reference is provided:
     * <ul>
     *   <li>If the boundary event's name is empty, a warning is added; otherwise, a success item is recorded.</li>
     *   <li>If an error is provided, it checks that both the error name and error code are not empty:
     *       if either is empty, an error item is added; if provided, a success item is recorded for each.</li>
     *   <li>If the {@code errorCodeVariable} attribute is missing, a warning is added; otherwise, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param boundaryEvent the {@link BoundaryEvent} to lint
     * @param issues        the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile      the BPMN file under lint
     * @param processId     the identifier of the BPMN process containing the event
     */
    public static void checkErrorBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = boundaryEvent.getId();

        // 1. Check if the BoundaryEvent's name is empty.
        if (isEmpty(boundaryEvent.getName())) {
            issues.add(BpmnElementLintItem.of(LinterSeverity.WARN, LintingType.BPMN_ERROR_BOUNDARY_EVENT_NAME_EMPTY,
                    elementId, bpmnFile, processId));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "BoundaryEvent has a non-empty name: '" + boundaryEvent.getName() + "'"));
        }

        // 2. Retrieve the ErrorEventDefinition.
        ErrorEventDefinition errorDef = (ErrorEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();

        // If an error is provided, check its name and error code.
        if (errorDef.getError() != null) {
            // 2a. Check the error name.
            if (isEmpty(errorDef.getError().getName())) {
                issues.add(BpmnElementLintItem.of(LinterSeverity.WARN, LintingType.BPMN_ERROR_BOUNDARY_EVENT_ERROR_NAME_EMPTY,
                        elementId, bpmnFile, processId));
            } else {
                issues.add(BpmnElementLintItem.success(
                        elementId, bpmnFile, processId,
                        "Error name is provided: '" + errorDef.getError().getName() + "'"
                ));
            }
            // 2b. Check the error code.
            if (isEmpty(errorDef.getError().getErrorCode())) {
                issues.add(BpmnElementLintItem.of(LinterSeverity.ERROR, LintingType.BPMN_ERROR_BOUNDARY_EVENT_ERROR_CODE_EMPTY,
                        elementId, bpmnFile, processId));
            } else {
                issues.add(BpmnElementLintItem.success(
                        elementId, bpmnFile, processId,
                        "Error code is provided: '" + errorDef.getError().getErrorCode() + "'"));
            }
        }

        // 3. Check the errorCodeVariable attribute.
        String errorCodeVariable = errorDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "errorCodeVariable");
        if (isEmpty(errorCodeVariable)) {
            issues.add(BpmnElementLintItem.of(LinterSeverity.WARN, LintingType.BPMN_ERROR_BOUNDARY_EVENT_ERROR_CODE_VARIABLE_EMPTY,
                    elementId, bpmnFile, processId));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "errorCodeVariable is provided: '" + errorCodeVariable + "'"
            ));
        }
    }

    /**
     * Lints a {@link ConditionalEventDefinition} for an Intermediate Catch Event.
     * <p>
     * This method performs several checks:
     * <ul>
     *   <li>Warns if the event name is empty; otherwise, records a success item.</li>
     *   <li>Errors if the conditional event variable name is empty; otherwise, records a success item.</li>
     *   <li>Errors if the {@code variableEvents} attribute is empty; otherwise, records a success item.</li>
     *   <li>
     *       If the condition type attribute is empty but a condition expression is provided, it assumes "expression" and records a success item.
     *       If the condition type is provided but is not "expression", an informational issue is logged and a success item is recorded.
     *       If the condition type is "expression", a success item is recorded.
     *   </li>
     *   <li>
     *       If the condition type is "expression" and the condition expression is empty, an error is recorded;
     *       otherwise, a success item is recorded.
     *   </li>
     * </ul>
     * </p>
     *
     * @param catchEvent the Conditional Intermediate Catch Event to lint
     * @param issues     the list of {@link BpmnElementLintItem} to which linter issues or success items will be added
     * @param bpmnFile    the BPMN file associated with the event
     * @param processId   the BPMN process identifier containing the event
     */
    public static void checkConditionalEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = catchEvent.getId();

        // 1. Check event name
        String eventName = catchEvent.getName();
        if (isEmpty(eventName)) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.WARN, LintingType.BPMN_FLOATING_ELEMENT,
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event name is empty"));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event name is provided: '" + eventName + "'"));
        }

        // 2. Get the ConditionalEventDefinition (assuming the first event definition is ConditionalEventDefinition).
        ConditionalEventDefinition condDef =
                (ConditionalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        // 3. Check conditional event variable name.
        String variableName = condDef.getCamundaVariableName();
        if (isEmpty(variableName)) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR, LintingType.BPMN_FLOATING_ELEMENT,
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variable name is empty"));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variable name is provided: '" + variableName + "'"
            ));
        }

        // 4. Check variableEvents attribute.
        String variableEvents = condDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "variableEvents");
        if (isEmpty(variableEvents)) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR, LintingType.BPMN_FLOATING_ELEMENT,
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variableEvents is empty"));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variableEvents is provided: '" + variableEvents + "'"
            ));
        }

        // 5. Check conditionType attribute.
        String conditionType = condDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "conditionType");

        if (isEmpty(conditionType)) {
            if (condDef.getCondition() != null && !isEmpty(condDef.getCondition().getRawTextContent())) {
                conditionType = "expression";
                issues.add(BpmnElementLintItem.success(
                        elementId, bpmnFile, processId,
                        "Condition type assumed to be 'expression' as condition expression is provided."));
            } else {
                issues.add(new BpmnElementLintItem(
                        LinterSeverity.ERROR, LintingType.BPMN_FLOATING_ELEMENT,
                        elementId, bpmnFile, processId,
                        "Conditional Intermediate Catch Event condition type is empty"));
            }
        } else if (!"expression".equalsIgnoreCase(conditionType)) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.INFO, LintingType.BPMN_FLOATING_ELEMENT,
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is not 'expression': " + conditionType));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is 'expression'"));
        }

        // 6. Check condition expression (only if condition type is 'expression').
        if ("expression".equalsIgnoreCase(conditionType)) {
            if (condDef.getCondition() == null || isEmpty(condDef.getCondition().getRawTextContent())) {
                issues.add(new BpmnElementLintItem(
                        LinterSeverity.ERROR, LintingType.BPMN_FLOATING_ELEMENT,
                        elementId, bpmnFile, processId,
                        "Conditional Intermediate Catch Event expression is empty"));
            } else {
                issues.add(BpmnElementLintItem.success(
                        elementId, bpmnFile, processId,
                        "Condition expression is provided: '" + condDef.getCondition().getRawTextContent() + "'"));
            }
        }
    }
}

