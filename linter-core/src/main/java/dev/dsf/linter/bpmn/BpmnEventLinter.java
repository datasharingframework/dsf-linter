package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.item.BpmnElementLintItem;
import dev.dsf.linter.util.bpmn.linters.*;
import org.camunda.bpm.model.bpmn.instance.*;

import java.io.File;
import java.util.List;

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
 * @see dev.dsf.linter.util.bpmn.linters.BpmnStartEventLinter
 * @see dev.dsf.linter.util.bpmn.linters.BpmnEndEventLinter
 * @see dev.dsf.linter.util.bpmn.linters.BpmnIntermediateThrowEventLinter
 * @see dev.dsf.linter.util.bpmn.linters.BpmnIntermediateCatchEventLinter
 * @see dev.dsf.linter.util.bpmn.linters.BpmnBoundaryEventLinter
 * @see dev.dsf.linter.util.bpmn.linters.BpmnMessageEventImplementationLinter
 * @since 1.0
 */
public record BpmnEventLinter(File projectRoot) {

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
    public BpmnEventLinter {
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
            BpmnStartEventLinter.lintMessageStartEvent(startEvent, issues, bpmnFile, processId, projectRoot);
        } else {
            BpmnStartEventLinter.lintGenericStartEvent(startEvent, issues, bpmnFile, processId, projectRoot);
        }
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
            BpmnIntermediateThrowEventLinter.lintMessageIntermediateThrowEvent(
                    throwEvent, issues, bpmnFile, processId, projectRoot);
        } else if (!throwEvent.getEventDefinitions().isEmpty()
                && throwEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {
            BpmnIntermediateThrowEventLinter.lintSignalIntermediateThrowEvent(
                    throwEvent, issues, bpmnFile, processId, projectRoot);
        }
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
            BpmnEndEventLinter.lintMessageEndEvent(endEvent, issues, bpmnFile, processId, projectRoot);
        } else if (!endEvent.getEventDefinitions().isEmpty()
                && endEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {
            BpmnEndEventLinter.lintSignalEndEvent(endEvent, issues, bpmnFile, processId, projectRoot);
        } else {
            BpmnEndEventLinter.lintGenericEndEvent(endEvent, issues, bpmnFile, processId, projectRoot);
        }
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
            BpmnIntermediateCatchEventLinter.lintMessageIntermediateCatchEvent(
                    catchEvent, issues, bpmnFile, processId, projectRoot);
        } else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof TimerEventDefinition) {
            BpmnIntermediateCatchEventLinter.lintTimerIntermediateCatchEvent(
                    catchEvent, issues, bpmnFile, processId, projectRoot);
        } else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof SignalEventDefinition) {
            BpmnIntermediateCatchEventLinter.lintSignalIntermediateCatchEvent(
                    catchEvent, issues, bpmnFile, processId, projectRoot);
        } else if (!catchEvent.getEventDefinitions().isEmpty()
                && catchEvent.getEventDefinitions().iterator().next() instanceof ConditionalEventDefinition) {
            BpmnIntermediateCatchEventLinter.lintConditionalIntermediateCatchEvent(
                    catchEvent, issues, bpmnFile, processId, projectRoot);
        }
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
            BpmnBoundaryEventLinter.lintMessageBoundaryEvent(
                    boundaryEvent, issues, bpmnFile, processId, projectRoot);
        } else if (!boundaryEvent.getEventDefinitions().isEmpty()
                && boundaryEvent.getEventDefinitions().iterator().next() instanceof ErrorEventDefinition) {
            BpmnBoundaryEventLinter.lintErrorBoundaryEvent(
                    boundaryEvent, issues, bpmnFile, processId, projectRoot);
        }
    }
}