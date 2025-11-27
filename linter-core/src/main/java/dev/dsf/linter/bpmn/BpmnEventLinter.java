package dev.dsf.linter.bpmn;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.FloatingElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.resource.FhirResourceLocator;
import org.camunda.bpm.model.bpmn.instance.*;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static dev.dsf.linter.bpmn.BpmnElementLinter.*;
import static dev.dsf.linter.bpmn.BpmnModelUtils.extractImplementationClass;
import static dev.dsf.linter.classloading.ClassInspector.*;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Specialized linter class for validating BPMN event elements against business logic and FHIR-related constraints.
 * 
 * <p>
 * The {@code BpmnEventLinter} serves as a specialized component for performing comprehensive validation
 * of BPMN 2.0 event elements used in Camunda workflows. It validates Start Events, End Events,
 * Intermediate Events (both Throw and Catch), and Boundary Events to ensure compliance with naming
 * conventions, class implementation requirements, FHIR resource referencing rules, execution listener
 * class validation, and event-specific configuration requirements.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * This class is instantiated by {@link BpmnModelLinter} and provides specialized validation for
 * event elements. It delegates common validation tasks to utility methods in {@link BpmnElementLinter}
 * and {@link BpmnFieldInjectionLinter}, while handling event-specific validation logic internally.
 * </p>
 *
 * <h2>Validation Categories</h2>
 * <p>
 * The linter performs the following categories of checks:
 * </p>
 *
 * <h3>Start Event Validation</h3>
 * <ul>
 *   <li><strong>Message Start Events</strong>: Validates event names, verifies message definitions,
 *       checks FHIR ActivityDefinition and StructureDefinition references, validates field injections,
 *       and checks execution listener classes</li>
 *   <li><strong>Generic Start Events</strong>: Validates that start events not part of a SubProcess
 *       have non-empty names and checks execution listener classes</li>
 * </ul>
 *
 * <h3>End Event Validation</h3>
 * <ul>
 *   <li><strong>Message End Events</strong>: Validates event names, verifies implementation class existence
 *       and interface compliance, validates field injections including {@code profile},
 *       {@code messageName}, and {@code instantiatesCanonical}, and checks execution listener classes</li>
 *   <li><strong>Signal End Events</strong>: Validates event names, signal definitions, and checks
 *       execution listener classes</li>
 *   <li><strong>Generic End Events</strong>: Validates that end events not part of a SubProcess have names,
 *       ensures that end events inside SubProcesses have {@code asyncAfter} set to true for proper
 *       asynchronous execution, and checks execution listener classes</li>
 * </ul>
 *
 * <h3>Intermediate Throw Event Validation</h3>
 * <ul>
 *   <li><strong>Message Intermediate Throw Events</strong>: Validates event names, verifies implementation
 *       class existence and element-specific interface compliance, validates field injections, checks
 *       message references, and checks execution listener classes</li>
 *   <li><strong>Signal Intermediate Throw Events</strong>: Validates event names, signal definitions,
 *       and checks execution listener classes</li>
 * </ul>
 *
 * <h3>Intermediate Catch Event Validation</h3>
 * <ul>
 *   <li><strong>Message Intermediate Catch Events</strong>: Validates event names, verifies message definitions,
 *       checks FHIR ActivityDefinition and StructureDefinition references, and checks execution listener classes</li>
 *   <li><strong>Timer Intermediate Catch Events</strong>: Validates event names, timer definitions
 *       (timeDate, timeCycle, timeDuration) including placeholder validation for dynamic timer values,
 *       and checks execution listener classes</li>
 *   <li><strong>Signal Intermediate Catch Events</strong>: Validates event names, signal definitions,
 *       and checks execution listener classes</li>
 *   <li><strong>Conditional Intermediate Catch Events</strong>: Validates event names, variable names,
 *       variableEvents configuration, condition types, condition expressions, and checks execution
 *       listener classes</li>
 * </ul>
 *
 * <h3>Boundary Event Validation</h3>
 * <ul>
 *   <li><strong>Message Boundary Events</strong>: Validates event names, verifies message definitions,
 *       checks FHIR ActivityDefinition and StructureDefinition references, and checks execution listener classes</li>
 *   <li><strong>Error Boundary Events</strong>: Validates event names, error definitions (error name
 *       and error code), errorCodeVariable assignments, and checks execution listener classes. Issues
 *       warnings for missing names and errors for missing critical error configuration</li>
 * </ul>
 *
 * <h3>Implementation Class Validation</h3>
 * <p>
 * For message events (Intermediate Throw Events and End Events), the linter validates:
 * </p>
 * <ul>
 *   <li><strong>Class Existence</strong>: Verifies that implementation classes exist on the project classpath</li>
 *   <li><strong>Element-Specific Interface Compliance</strong>: Validates that implementation classes
 *       implement the correct interface based on the element type and API version (e.g., MessageIntermediateThrowEvent
 *       interface for V2 API, JavaDelegate for V1 API)</li>
 *   <li><strong>API Version Isolation</strong>: Uses version-specific interface requirements to ensure
 *       compatibility with the correct DSF BPE API version</li>
 * </ul>
 *
 * <h3>Execution Listener Validation</h3>
 * <p>
 * For all event types, the linter validates execution listener classes:
 * </p>
 * <ul>
 *   <li><strong>Class Existence</strong>: Verifies that execution listener classes referenced in the BPMN
 *       model exist on the project classpath</li>
 *   <li><strong>Interface Compliance</strong>: Validates that execution listener classes implement the
 *       required Camunda execution listener interfaces (e.g., {@code ExecutionListener})</li>
 *   <li><strong>Consistency</strong>: Ensures that all execution listeners attached to event elements
 *       are properly configured and can be instantiated at runtime</li>
 * </ul>
 *
 * <h3>FHIR-Specific Validation</h3>
 * <p>
 * For message events, the following FHIR-related validations are performed:
 * </p>
 * <ul>
 *   <li><strong>ActivityDefinition Validation</strong>: Verifies that message names correspond to existing
 *       FHIR ActivityDefinition resources</li>
 *   <li><strong>StructureDefinition Validation</strong>: Verifies that message names correspond to existing
 *       FHIR StructureDefinition resources</li>
 *   <li><strong>Field Injection Validation</strong>: Validates Camunda field injection values for
 *       {@code profile}, {@code messageName}, and {@code instantiatesCanonical} fields</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * File projectRoot = new File("/path/to/project");
 * BpmnEventLinter linter = new BpmnEventLinter(projectRoot);
 * 
 * List<BpmnElementLintItem> issues = new ArrayList<>();
 * StartEvent startEvent = ...; // obtained from BPMN model
 * File bpmnFile = new File("process.bpmn");
 * String processId = "myProcess";
 * 
 * linter.lintStartEvent(startEvent, issues, bpmnFile, processId);
 * 
 * for (BpmnElementLintItem issue : issues) {
 *     System.out.println(issue.getSeverity() + ": " + issue.getMessage());
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is not thread-safe. Each linting operation uses the instance's {@code projectRoot} field,
 * which requires external synchronization if the same instance is used across multiple threads.
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">Camunda Extension Elements</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 *
 * @see BpmnModelLinter
 * @see BpmnElementLinter
 * @see BpmnFieldInjectionLinter
 * @see BpmnSignalIntermediateThrowEventNameEmptyLintItem
 * @see BpmnSignalIntermediateThrowEventSignalEmptyLintItem
 * @see BpmnSignalEndEventNameEmptyLintItem
 * @see BpmnSignalEndEventSignalEmptyLintItem
 * @see BpmnEndEventInsideSubProcessShouldHaveAsyncAfterTrueLintItem
 * @see BpmnFloatingElementLintItem
 * @since 1.0
 */
public class BpmnEventLinter {

    private final File projectRoot;

    /**
     * Constructs a new {@code BpmnEventLinter} instance with the specified project root directory.
     * 
     * <p>
     * The project root is used by the linter to locate compiled classes, FHIR resources, and other
     * project artifacts required for validation. It typically points to the root directory of a Maven
     * or Gradle project containing build output directories such as {@code target/classes} or
     * {@code build/classes}.
     * </p>
     *
     * @param projectRoot the root directory of the project; must not be {@code null}
     * @throws IllegalArgumentException if {@code projectRoot} is {@code null}
     */
    public BpmnEventLinter(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    // ==================== START EVENT ====================

    /**
     * Lints a {@link StartEvent}.
     */
    public void lintStartEvent(
            StartEvent startEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        if (!startEvent.getEventDefinitions().isEmpty()
                && startEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {
            lintMessageStartEvent(startEvent, issues, bpmnFile, processId);
        } else {
            lintGenericStartEvent(startEvent, issues, bpmnFile, processId);
        }
    }

    private void lintMessageStartEvent(
            StartEvent startEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = startEvent.getId();
        var locator = FhirResourceLocator.create(projectRoot);

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
            String msgName = messageDef.getMessage().getName();
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Message name is not empty: '" + msgName + "'"));

            // 3. Check FHIR references
            lintFhirReferences(msgName, elementId, issues, bpmnFile, processId, locator);
        }
        // 4. Validate field injections
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                startEvent, issues, bpmnFile, processId, projectRoot);

        // Check execution listener classes
        checkExecutionListenerClasses(startEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    private void lintGenericStartEvent(
            StartEvent startEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

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

    // ==================== INTERMEDIATE THROW EVENT ====================

    /**
     * Lints an {@link IntermediateThrowEvent}.
     */
    public void lintIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        if (!throwEvent.getEventDefinitions().isEmpty()
                && throwEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {
            lintMessageIntermediateThrowEvent(throwEvent, issues, bpmnFile, processId);
        } else if (!throwEvent.getEventDefinitions().isEmpty()
                && throwEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {
            lintSignalIntermediateThrowEvent(throwEvent, issues, bpmnFile, processId);
        }
    }

    private void lintMessageIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = throwEvent.getId();
        ApiVersion apiVersion = ApiVersionHolder.getVersion();

        // 1. Check event name
        if (isEmpty(throwEvent.getName())) {
            issues.add(new BpmnEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Message Intermediate Throw Event has a non-empty name: '" + throwEvent.getName() + "'"));
        }

        // 2. Validate implementation class with ELEMENT-SPECIFIC check
        Optional<String> implClassOpt = extractImplementationClass(throwEvent);

        if (implClassOpt.isEmpty()) {
            issues.add(new BpmnMessageSendEventImplementationClassEmptyLintItem(elementId, bpmnFile, processId));
        } else {
            String implClass = implClassOpt.get();
            lintMessageEventImplementationClass(
                    implClass, elementId, BpmnElementType.MESSAGE_INTERMEDIATE_THROW_EVENT,
                    issues, bpmnFile, processId, apiVersion);
        }

        // 3. Validate field injections
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                throwEvent, issues, bpmnFile, processId, projectRoot);

        // 4. Check message reference
        MessageEventDefinition msgDef =
                (MessageEventDefinition) throwEvent.getEventDefinitions().iterator().next();

        if (msgDef.getMessage() != null) {
            String messageName = msgDef.getMessage().getName();
            issues.add(new BpmnMessageIntermediateThrowEventHasMessageLintItem(
                    elementId, bpmnFile, processId,
                    "Message Intermediate Throw Event has a message with name: " + messageName));
        } else {
            issues.add(new BpmnMessageIntermediateThrowEventHasMessageLintItem(
                    elementId, bpmnFile, processId));
        }

        // Check execution listener classes
        checkExecutionListenerClasses(throwEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    private void lintSignalIntermediateThrowEvent(
            IntermediateThrowEvent throwEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = throwEvent.getId();

        // 1. Check event name
        if (isEmpty(throwEvent.getName())) {
            issues.add(new BpmnSignalIntermediateThrowEventNameEmptyLintItem(
                    elementId, bpmnFile, processId));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Throw Event has a non-empty name: '" + throwEvent.getName() + "'"));
        }

        // 2. Check signal definition
        SignalEventDefinition def =
                (SignalEventDefinition) throwEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || isEmpty(def.getSignal().getName())) {
            issues.add(new BpmnSignalIntermediateThrowEventSignalEmptyLintItem(
                    elementId, bpmnFile, processId));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Signal is present with name: '" + def.getSignal().getName() + "'"));
        }

        // Check execution listener classes
        checkExecutionListenerClasses(throwEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    // ==================== END EVENT ====================

    /**
     * Lints an {@link EndEvent}.
     */
    public void lintEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        if (!endEvent.getEventDefinitions().isEmpty()
                && endEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {
            lintMessageEndEvent(endEvent, issues, bpmnFile, processId);
        } else if (!endEvent.getEventDefinitions().isEmpty()
                && endEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {
            lintSignalEndEvent(endEvent, issues, bpmnFile, processId);
        } else {
            lintGenericEndEvent(endEvent, issues, bpmnFile, processId);
        }
    }

    private void lintMessageEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = endEvent.getId();
        ApiVersion apiVersion = ApiVersionHolder.getVersion();

        // 1. Check event name
        if (isEmpty(endEvent.getName())) {
            issues.add(new BpmnEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Message End Event has a non-empty name: '" + endEvent.getName() + "'"));
        }

        // 2. Validate implementation class with ELEMENT-SPECIFIC check
        Optional<String> implClassOpt = extractImplementationClass(endEvent);

        if (implClassOpt.isEmpty()) {
            issues.add(new BpmnMessageSendEventImplementationClassEmptyLintItem(elementId, bpmnFile, processId));
        } else {
            String implClass = implClassOpt.get();
            lintMessageEventImplementationClass(
                    implClass, elementId, BpmnElementType.MESSAGE_END_EVENT,
                    issues, bpmnFile, processId, apiVersion);
        }

        // 3. Validate field injections
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                endEvent, issues, bpmnFile, processId, projectRoot);

        // Check execution listener classes
        checkExecutionListenerClasses(endEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    private void lintGenericEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = endEvent.getId();

        // 1. Name check for non-SubProcess end events
        if (!(endEvent.getParentElement() instanceof SubProcess)) {
            if (isEmpty(endEvent.getName())) {
                issues.add(new BpmnEndEventNotPartOfSubProcessLintItem(elementId, bpmnFile, processId));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "End event has a non-empty name: '" + endEvent.getName() + "'"));
            }
        }

        // 2. AsyncAfter check for SubProcess end events
        if (endEvent.getParentElement() instanceof SubProcess) {
            if (!endEvent.isCamundaAsyncAfter()) {
                issues.add(new BpmnEndEventInsideSubProcessShouldHaveAsyncAfterTrueLintItem(
                        elementId, bpmnFile, processId));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "End Event inside a SubProcess has asyncAfter=true"));
            }
        }

        // Check execution listener classes
        checkExecutionListenerClasses(endEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    private void lintSignalEndEvent(
            EndEvent endEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = endEvent.getId();

        // 1. Check event name
        if (isEmpty(endEvent.getName())) {
            issues.add(new BpmnSignalEndEventNameEmptyLintItem(
                    elementId, bpmnFile, processId));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Signal End Event has a non-empty name: '" + endEvent.getName() + "'"));
        }

        // 2. Check signal definition
        SignalEventDefinition def =
                (SignalEventDefinition) endEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || isEmpty(def.getSignal().getName())) {
            issues.add(new BpmnSignalEndEventSignalEmptyLintItem(
                    elementId, bpmnFile, processId));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Signal is present with name: '" + def.getSignal().getName() + "'"));
        }

        // Check execution listener classes
        checkExecutionListenerClasses(endEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    // ==================== INTERMEDIATE CATCH EVENT ====================

    /**
     * Lints an {@link IntermediateCatchEvent}.
     */
    public void lintIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {
            lintMessageIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        } else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof TimerEventDefinition) {
            lintTimerIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        } else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {
            lintSignalIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        } else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof ConditionalEventDefinition) {
            lintConditionalIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
        }
    }

    private void lintMessageIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = catchEvent.getId();

        // 1. Check event name
        if (isEmpty(catchEvent.getName())) {
            issues.add(new BpmnMessageIntermediateCatchEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Message Intermediate Catch Event has a non-empty name: '" + catchEvent.getName() + "'"));
        }

        // 2. Check message definition
        MessageEventDefinition def =
                (MessageEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        if (def.getMessage() == null || isEmpty(def.getMessage().getName())) {
            issues.add(new BpmnMessageIntermediateCatchEventMessageNameEmptyLintItem(elementId, bpmnFile, processId));
        } else {
            String msgName = def.getMessage().getName();
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Message name is not empty: '" + msgName + "'"));

            checkMessageName(msgName, issues, elementId, bpmnFile, processId, projectRoot);
        }

        // Check execution listener classes
        checkExecutionListenerClasses(catchEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    private void lintTimerIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = catchEvent.getId();

        // 1. Check event name
        if (isEmpty(catchEvent.getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Timer Intermediate Catch Event name is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.WARN,
                    FloatingElementType.TIMER_INTERMEDIATE_CATCH_EVENT_NAME_IS_EMPTY));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
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

    private void lintSignalIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = catchEvent.getId();

        // 1. Check event name
        if (isEmpty(catchEvent.getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Catch Event name is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.WARN,
                    FloatingElementType.SIGNAL_INTERMEDIATE_CATCH_EVENT_NAME_IS_EMPTY));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Signal Intermediate Catch Event has a non-empty name: '" + catchEvent.getName() + "'"));
        }

        // 2. Check signal definition
        SignalEventDefinition def =
                (SignalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        if (def.getSignal() == null || isEmpty(def.getSignal().getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Signal is empty in Signal Intermediate Catch Event",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.ERROR,
                    FloatingElementType.SIGNAL_IS_EMPTY_IN_SIGNAL_INTERMEDIATE_CATCH_EVENT));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Signal is present with name: '" + def.getSignal().getName() + "'"));
        }

        // Check execution listener classes
        checkExecutionListenerClasses(catchEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    private void lintConditionalIntermediateCatchEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        checkConditionalEvent(catchEvent, issues, bpmnFile, processId);

        // Check execution listener classes
        checkExecutionListenerClasses(catchEvent, catchEvent.getId(), issues, bpmnFile, processId, projectRoot);
    }

    // ==================== BOUNDARY EVENT ====================

    /**
     * Lints a {@link BoundaryEvent}.
     */
    public void lintBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        if (!boundaryEvent.getEventDefinitions().isEmpty()
                && boundaryEvent.getEventDefinitions().iterator().next() instanceof MessageEventDefinition) {
            lintMessageBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
        } else if (!boundaryEvent.getEventDefinitions().isEmpty()
                && boundaryEvent.getEventDefinitions().iterator().next() instanceof ErrorEventDefinition) {
            lintErrorBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
        }
    }

    private void lintMessageBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = boundaryEvent.getId();
        var locator = FhirResourceLocator.create(projectRoot);

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
            String msgName = def.getMessage().getName();
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Message name is not empty: '" + msgName + "'"));

            lintFhirReferences(msgName, elementId, issues, bpmnFile, processId, locator);
        }

        // Check execution listener classes
        checkExecutionListenerClasses(boundaryEvent, elementId, issues, bpmnFile, processId, projectRoot);
    }

    private void lintErrorBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        checkErrorBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);

        // Check execution listener classes
        checkExecutionListenerClasses(boundaryEvent, boundaryEvent.getId(), issues, bpmnFile, processId, projectRoot);
    }

    // ==================== IMPLEMENTATION CLASS VALIDATION ====================

    /**
     * Validates implementation class for Message Events (IntermediateThrow, End)
     * with element-specific interface requirements.
     */
    private void lintMessageEventImplementationClass(
            String implClass,
            String elementId,
            BpmnElementType elementType,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            ApiVersion apiVersion) {

        // Step 1: Check class existence
        if (!classExists(implClass, projectRoot)) {
            issues.add(new BpmnMessageSendEventImplementationClassNotFoundLintItem(
                    elementId, bpmnFile, processId, implClass));
            return;
        }

        // Step 2: ELEMENT-SPECIFIC interface check
        if (doesNotImplementCorrectInterface(implClass, projectRoot, apiVersion, elementType)) {
            String expectedInterface = getExpectedInterfaceDescription(apiVersion, elementType);

            switch (apiVersion) {
                case V1 -> issues.add(
                        new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateLintItem(
                                elementId, bpmnFile, processId, implClass));
                case V2 -> issues.add(
                        new BpmnEndOrIntermediateThrowEventMissingInterfaceLintItem(
                                elementId, bpmnFile, processId, implClass,
                                "Implementation class '" + implClass
                                        + "' does not implement " + expectedInterface + "."));
            }
            return;
        }

        // Step 3: Success
        String implementedInterface = findImplementedInterface(implClass, projectRoot, apiVersion, elementType);
        String interfaceName = implementedInterface != null
                ? getSimpleName(implementedInterface)
                : getExpectedInterfaceDescription(apiVersion, elementType);

        issues.add(new BpmnElementLintItemSuccess(
                elementId, bpmnFile, processId,
                "Implementation class '" + implClass + "' implements " + interfaceName + "."));
    }

    // ==================== HELPER METHODS ====================

    /**
     * Validates FHIR references (ActivityDefinition and StructureDefinition).
     */
    private void lintFhirReferences(
            String msgName,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            FhirResourceLocator locator) {

        boolean activityDefFound = locator.activityDefinitionExists(msgName, projectRoot);

        if (!activityDefFound) {
            issues.add(new BpmnNoActivityDefinitionFoundForMessageLintItem(
                    LinterSeverity.ERROR, elementId, bpmnFile, processId, msgName,
                    "No ActivityDefinition found for messageName: " + msgName));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "ActivityDefinition found for messageName: '" + msgName + "'"));

            // Only check StructureDefinition if ActivityDefinition was found
            if (!locator.structureDefinitionExists(msgName, projectRoot)) {
                issues.add(new BpmnNoStructureDefinitionFoundForMessageLintItem(
                        LinterSeverity.ERROR, elementId, bpmnFile, processId, msgName,
                        "No StructureDefinition found for messageName: " + msgName));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "StructureDefinition found for messageName: '" + msgName + "'"));
            }
        }
    }
}