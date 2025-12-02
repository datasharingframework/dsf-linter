package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.bpmn.BpmnFieldInjectionLinter;
import dev.dsf.linter.output.item.*;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkExecutionListenerClasses;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Utility class for validating BPMN start events.
 * <p>
 * This class provides validation methods to ensure that start events are properly configured
 * with names, message definitions, FHIR resource references, and execution listener classes.
 * </p>
 */
public final class BpmnStartEventLinter {

    private BpmnStartEventLinter() {
        // Utility class - no instantiation
    }

    /**
     * Lints a message start event.
     *
     * @param startEvent the start event to lint
     * @param issues     the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile   the BPMN file under lint
     * @param processId  the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintMessageStartEvent(
            StartEvent startEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = startEvent.getId();

        // 1. Check event name
        if (isEmpty(startEvent.getName())) {
            issues.add(new BpmnEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Start event has a non-empty name: '" + startEvent.getName() + "'"));
        }

        // 2. Check message definition
        MessageEventDefinition messageDef =
                (MessageEventDefinition) startEvent.getEventDefinitions().iterator().next();

        if (messageDef.getMessage() == null || isEmpty(messageDef.getMessage().getName())) {
            issues.add(new BpmnMessageStartEventMessageNameEmptyLintItem(elementId, bpmnFile, processId));
        } else {
            BpmnMessageLinter.lintMessageEventDefinition(messageDef, elementId, issues, bpmnFile, processId, projectRoot);
        }
        // 4. Validate field injections
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                startEvent, issues, bpmnFile, processId, projectRoot);

        // Check execution listener classes
        checkExecutionListenerClasses(startEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * Lints a generic start event (non-message).
     *
     * @param startEvent the start event to lint
     * @param issues     the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile   the BPMN file under lint
     * @param processId  the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintGenericStartEvent(
            StartEvent startEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = startEvent.getId();

        if (!(startEvent.getParentElement() instanceof SubProcess)) {
            if (isEmpty(startEvent.getName())) {
                issues.add(new BpmnStartEventNotPartOfSubProcessLintItem(elementId, bpmnFile, processId));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "Generic start event has a non-empty name: '" + startEvent.getName() + "'"));
            }
        }

        // Check execution listener classes
        checkExecutionListenerClasses(startEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }
}

