package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.output.item.*;
import org.camunda.bpm.model.bpmn.instance.BoundaryEvent;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkExecutionListenerClasses;
import static dev.dsf.linter.bpmn.BpmnElementLinter.checkErrorBoundaryEvent;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Utility class for validating BPMN boundary events.
 * <p>
 * This class provides validation methods to ensure that boundary events are properly configured
 * with names, message definitions, error definitions, FHIR resource references, and execution listener classes.
 * </p>
 */
public final class BpmnBoundaryEventLinter {

    private BpmnBoundaryEventLinter() {
        // Utility class - no instantiation
    }

    /**
     * Lints a message boundary event.
     *
     * @param boundaryEvent the boundary event to lint
     * @param issues        the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile      the BPMN file under lint
     * @param processId     the identifier of the BPMN process containing the event
     * @param projectRoot   the project root directory
     */
    public static void lintMessageBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = boundaryEvent.getId();

        // 1. Check event name
        if (isEmpty(boundaryEvent.getName())) {
            issues.add(new BpmnMessageBoundaryEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Message Boundary Event has a non-empty name: '" + boundaryEvent.getName() + "'"));
        }

        // 2. Check message definition
        MessageEventDefinition def =
                (MessageEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();

        if (def.getMessage() == null || isEmpty(def.getMessage().getName())) {
            issues.add(new BpmnMessageBoundaryEventMessageNameEmptyLintItem(elementId, bpmnFile, processId));
        } else {
            BpmnMessageLinter.lintMessageEventDefinition(def, elementId, issues, bpmnFile, processId, projectRoot);
        }

        // Check execution listener classes
        checkExecutionListenerClasses(boundaryEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * Lints an error boundary event.
     *
     * @param boundaryEvent the boundary event to lint
     * @param issues        the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile      the BPMN file under lint
     * @param processId     the identifier of the BPMN process containing the event
     * @param projectRoot   the project root directory
     */
    public static void lintErrorBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        checkErrorBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);

        // Check execution listener classes
        checkExecutionListenerClasses(boundaryEvent, boundaryEvent.getId(), issues, bpmnFile, processId, projectRoot);
    }
}

