package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.bpmn.BpmnFieldInjectionLinter;
import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import org.camunda.bpm.model.bpmn.instance.IntermediateThrowEvent;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.camunda.bpm.model.bpmn.instance.SignalEventDefinition;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkExecutionListenerClasses;
import static dev.dsf.linter.util.bpmn.BpmnModelUtils.extractImplementationClass;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Utility class for validating BPMN intermediate throw events.
 * <p>
 * This class provides validation methods to ensure that intermediate throw events are properly configured
 * with names, implementation classes, signal definitions, and execution listener classes.
 * </p>
 */
public final class BpmnIntermediateThrowEventLinter {

    private BpmnIntermediateThrowEventLinter() {
        // Utility class - no instantiation
    }

    /**
     * Lints a message intermediate throw event.
     *
     * @param throwEvent  the intermediate throw event to lint
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintMessageIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = throwEvent.getId();
        ApiVersion apiVersion = ApiVersionHolder.getVersion();

        // 1. Check event name
        if (isEmpty(throwEvent.getName())) {
            issues.add(new BpmnElementLintItem(LinterSeverity.WARN, LintingType.BPMN_EVENT_NAME_EMPTY,
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Message Intermediate Throw Event has a non-empty name: '" + throwEvent.getName() + "'"));
        }

        // 2. Validate implementation class with ELEMENT-SPECIFIC check
        Optional<String> implClassOpt = extractImplementationClass(throwEvent);

        if (implClassOpt.isEmpty()) {
            issues.add(BpmnElementLintItem.of(LinterSeverity.ERROR, LintingType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_EMPTY,
                    elementId, bpmnFile, processId));
        } else {
            String implClass = implClassOpt.get();
            BpmnMessageEventImplementationLinter.lintMessageEventImplementationClass(
                    implClass, elementId, BpmnElementType.MESSAGE_INTERMEDIATE_THROW_EVENT,
                    issues, bpmnFile, processId, apiVersion, projectRoot);
        }

        // 3. Validate field injections
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                throwEvent, issues, bpmnFile, processId, projectRoot);

        // 4. Check message reference
        MessageEventDefinition msgDef =
                (MessageEventDefinition) throwEvent.getEventDefinitions().iterator().next();

        if (msgDef.getMessage() != null) {
            String messageName = msgDef.getMessage().getName();
            issues.add(new BpmnElementLintItem(LinterSeverity.ERROR, LintingType.BPMN_MESSAGE_INTERMEDIATE_THROW_EVENT_HAS_MESSAGE,
                    elementId, bpmnFile, processId,
                    "Message Intermediate Throw Event has a message with name: " + messageName));
        } else {
            issues.add(BpmnElementLintItem.of(LinterSeverity.ERROR, LintingType.BPMN_MESSAGE_INTERMEDIATE_THROW_EVENT_HAS_MESSAGE,
                    elementId, bpmnFile, processId));
        }

        // Check execution listener classes
        checkExecutionListenerClasses(throwEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * Lints a signal intermediate throw event.
     *
     * @param throwEvent  the intermediate throw event to lint
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the event
     * @param projectRoot the project root directory
     */
    public static void lintSignalIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String elementId = throwEvent.getId();

        // 1. Check event name
        if (isEmpty(throwEvent.getName())) {
            issues.add(BpmnElementLintItem.of(LinterSeverity.WARN, LintingType.BPMN_SIGNAL_INTERMEDIATE_THROW_EVENT_NAME_EMPTY,
                    elementId, bpmnFile, processId));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Throw Event has a non-empty name: '" + throwEvent.getName() + "'"));
        }

        // 2. Check signal definition
        SignalEventDefinition def =
                (SignalEventDefinition) throwEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || isEmpty(def.getSignal().getName())) {
            issues.add(BpmnElementLintItem.of(LinterSeverity.ERROR, LintingType.BPMN_SIGNAL_INTERMEDIATE_THROW_EVENT_SIGNAL_EMPTY,
                    elementId, bpmnFile, processId));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId, bpmnFile, processId,
                    "Signal is present with name: '" + def.getSignal().getName() + "'"));
        }

        // Check execution listener classes
        checkExecutionListenerClasses(throwEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }
}

