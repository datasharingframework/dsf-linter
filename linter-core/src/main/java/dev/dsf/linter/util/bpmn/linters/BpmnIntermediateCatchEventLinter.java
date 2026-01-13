package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.camunda.bpm.model.bpmn.instance.SignalEventDefinition;
import org.camunda.bpm.model.bpmn.instance.TimerEventDefinition;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.bpmn.BpmnElementLinter.*;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Utility class for validating BPMN intermediate catch events.
 * <p>
 * This class provides validation methods to ensure that intermediate catch events are properly configured
 * with names, message definitions, timer definitions, signal definitions, conditional event definitions,
 * and execution listener classes.
 * </p>
 */
public final class BpmnIntermediateCatchEventLinter {

    private BpmnIntermediateCatchEventLinter() {
        // Utility class - no instantiation
    }

    /**
     * Lints a message intermediate catch event.
     *
     * @param catchEvent  the intermediate catch event to lint
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintMessageIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = catchEvent.getId();

        // 1. Check event name
        if (isEmpty(catchEvent.getName())) {
            issues.add(new BpmnElementLintItem(LinterSeverity.WARN, LintingType.BPMN_MESSAGE_INTERMEDIATE_CATCH_EVENT_NAME_EMPTY,
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Message Intermediate Catch Event has a non-empty name: '" + catchEvent.getName() + "'"));
        }

        // 2. Check message definition
        MessageEventDefinition def =
                (MessageEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        if (def.getMessage() == null || isEmpty(def.getMessage().getName())) {
            issues.add(BpmnElementLintItem.of(LinterSeverity.ERROR, LintingType.BPMN_MESSAGE_INTERMEDIATE_CATCH_EVENT_MESSAGE_NAME_EMPTY,
                    elementId, bpmnFile, processId));
        } else {
            String msgName = def.getMessage().getName();
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Message name is not empty: '" + msgName + "'"));

            checkMessageName(msgName, issues, elementId, bpmnFile, processId, projectRoot);
        }

        // Check execution listener classes
        checkExecutionListenerClasses(catchEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * Lints a timer intermediate catch event.
     *
     * @param catchEvent  the intermediate catch event to lint
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintTimerIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = catchEvent.getId();

        // 1. Check event name
        if (isEmpty(catchEvent.getName())) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.WARN, LintingType.BPMN_FLOATING_ELEMENT,
                    elementId, bpmnFile, processId,
                    "Timer Intermediate Catch Event name is empty"));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Timer Intermediate Catch Event has a non-empty name: '" + catchEvent.getName() + "'"));
        }

        // 2. Check timer definition
        TimerEventDefinition timerDef =
                (TimerEventDefinition) catchEvent.getEventDefinitions().iterator().next();
        checkTimerDefinition(elementId, issues, bpmnFile, processId, timerDef);

        // Check execution listener classes
        checkExecutionListenerClasses(catchEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * Lints a signal intermediate catch event.
     *
     * @param catchEvent  the intermediate catch event to lint
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintSignalIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = catchEvent.getId();

        // 1. Check event name
        if (isEmpty(catchEvent.getName())) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.WARN, LintingType.BPMN_FLOATING_ELEMENT,
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Catch Event name is empty"));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Catch Event has a non-empty name: '" + catchEvent.getName() + "'"));
        }

        // 2. Check signal definition
        SignalEventDefinition def =
                (SignalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || isEmpty(def.getSignal().getName())) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR, LintingType.BPMN_FLOATING_ELEMENT,
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal Intermediate Catch Event"));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Signal is present with name: '" + def.getSignal().getName() + "'"));
        }

        // Check execution listener classes
        checkExecutionListenerClasses(catchEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * Lints a conditional intermediate catch event.
     *
     * @param catchEvent  the intermediate catch event to lint
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintConditionalIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        checkConditionalEvent(catchEvent, issues, bpmnFile, processId);

        // Check execution listener classes
        checkExecutionListenerClasses(catchEvent, catchEvent.getId(), issues, bpmnFile, processId, projectRoot);
    }
}

