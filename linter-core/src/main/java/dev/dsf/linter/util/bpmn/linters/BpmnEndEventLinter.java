package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.bpmn.BpmnFieldInjectionLinter;
import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.SignalEventDefinition;
import org.camunda.bpm.model.bpmn.instance.SubProcess;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkExecutionListenerClasses;
import static dev.dsf.linter.util.bpmn.BpmnModelUtils.extractImplementationClass;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Utility class for validating BPMN end events.
 * <p>
 * This class provides validation methods to ensure that end events are properly configured
 * with names, implementation classes, signal definitions, and execution listener classes.
 * </p>
 */
public final class BpmnEndEventLinter {

    private BpmnEndEventLinter() {
        // Utility class - no instantiation
    }

    /**
     * Lints a message end event.
     *
     * @param endEvent   the end event to lint
     * @param issues     the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile   the BPMN file under lint
     * @param processId  the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintMessageEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = endEvent.getId();
        ApiVersion apiVersion = ApiVersionHolder.getVersion();

        // 1. Check event name
        if (isEmpty(endEvent.getName())) {
            issues.add(new BpmnElementLintItem(LinterSeverity.WARN, LintingType.BPMN_EVENT_NAME_EMPTY,
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Message End Event has a non-empty name: '" + endEvent.getName() + "'"));
        }

        // 2. Validate implementation class with ELEMENT-SPECIFIC check
        Optional<String> implClassOpt = extractImplementationClass(endEvent);

        if (implClassOpt.isEmpty()) {
            issues.add(BpmnElementLintItem.of(LinterSeverity.ERROR, LintingType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_EMPTY,
                    elementId, bpmnFile, processId));
        } else {
            String implClass = implClassOpt.get();
            BpmnMessageEventImplementationLinter.lintMessageEventImplementationClass(
                    implClass, elementId, BpmnElementType.MESSAGE_END_EVENT,
                    issues, bpmnFile, processId, apiVersion, projectRoot);
        }

        // 3. Validate field injections
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                endEvent, issues, bpmnFile, processId, projectRoot);

        // Check execution listener classes
        checkExecutionListenerClasses(endEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * Lints a generic end event (non-message, non-signal).
     *
     * @param endEvent   the end event to lint
     * @param issues     the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile   the BPMN file under lint
     * @param processId  the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintGenericEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = endEvent.getId();

        // 1. Name check for non-SubProcess end events
        if (!(endEvent.getParentElement() instanceof SubProcess)) {
            if (isEmpty(endEvent.getName())) {
                issues.add(BpmnElementLintItem.of(LinterSeverity.WARN, LintingType.BPMN_END_EVENT_NOT_PART_OF_SUB_PROCESS,
                        elementId, bpmnFile, processId));
            } else {
                issues.add(BpmnElementLintItem.success(
                        elementId, bpmnFile, processId,
                        "End event has a non-empty name: '" + endEvent.getName() + "'"));
            }
        }

        // 2. AsyncAfter check for SubProcess end events
        if (endEvent.getParentElement() instanceof SubProcess) {
            if (!endEvent.isCamundaAsyncAfter()) {
                issues.add(BpmnElementLintItem.of(LinterSeverity.WARN, LintingType.BPMN_END_EVENT_INSIDE_SUB_PROCESS_SHOULD_HAVE_ASYNC_AFTER_TRUE,
                        elementId, bpmnFile, processId));
            } else {
                issues.add(BpmnElementLintItem.success(
                        elementId, bpmnFile, processId,
                        "End Event inside a SubProcess has asyncAfter=true"));
            }
        }

        // Check execution listener classes
        checkExecutionListenerClasses(endEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * Lints a signal end event.
     *
     * @param endEvent   the end event to lint
     * @param issues     the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile   the BPMN file under lint
     * @param processId  the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintSignalEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = endEvent.getId();

        // 1. Check event name
        if (isEmpty(endEvent.getName())) {
            issues.add(BpmnElementLintItem.of(LinterSeverity.WARN, LintingType.BPMN_SIGNAL_END_EVENT_NAME_EMPTY,
                    elementId, bpmnFile, processId));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Signal End Event has a non-empty name: '" + endEvent.getName() + "'"));
        }

        // 2. Check signal definition
        SignalEventDefinition def =
                (SignalEventDefinition) endEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || isEmpty(def.getSignal().getName())) {
            issues.add(BpmnElementLintItem.of(LinterSeverity.ERROR, LintingType.BPMN_SIGNAL_END_EVENT_SIGNAL_EMPTY,
                    elementId, bpmnFile, processId));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Signal is present with name: '" + def.getSignal().getName() + "'"));
        }

        // Check execution listener classes
        checkExecutionListenerClasses(endEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }
}

