package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.item.BpmnElementLintItem;
import dev.dsf.linter.util.bpmn.linters.BpmnEventLinter;
import dev.dsf.linter.util.bpmn.linters.BpmnListenerLinter;
import dev.dsf.linter.util.bpmn.linters.BpmnMessageLinter;
import dev.dsf.linter.util.bpmn.linters.BpmnTimerLinter;

import java.io.File;
import java.util.List;

/**
 * Utility class providing element-level linting methods with proper API version isolation.
 * 
 * <p>
 * The {@code BpmnElementLinter} serves as a facade that delegates to specialized validation classes
 * in the {@code dev.dsf.linter.util.bpmn.validation} package. It maintains backward compatibility
 * with existing code while organizing validation logic into focused utility classes. This class
 * provides a unified interface for validating various BPMN element configurations, including message
 * names, execution listeners, task listeners, timer definitions, error boundary events, and
 * conditional events.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * This class is designed as a utility class with static methods that delegate to specialized
 * validation classes. The delegation pattern ensures that validation logic is properly organized
 * while maintaining a consistent API for existing code. The specialized validation classes are:
 * </p>
 * <ul>
 *   <li>{@link BpmnMessageLinter} - Validates message names against FHIR ActivityDefinition and
 *       StructureDefinition resources</li>
 *   <li>{@link BpmnListenerLinter} - Validates execution and task listener classes with API
 *       version-specific interface requirements</li>
 *   <li>{@link BpmnTimerLinter} - Validates timer event definitions including timeDate, timeCycle,
 *       and timeDuration expressions</li>
 *   <li>{@link BpmnEventLinter} - Validates error boundary events and conditional intermediate
 *       catch events</li>
 * </ul>
 *
 * <h2>Validation Categories</h2>
 * <p>
 * This class provides validation methods for the following categories:
 * </p>
 *
 * <h3>Message Name Validation</h3>
 * <ul>
 *   <li><strong>FHIR Resource Validation</strong>: Validates that message names correspond to existing
 *       FHIR ActivityDefinition and StructureDefinition resources in the project</li>
 *   <li><strong>Cross-Reference Validation</strong>: Ensures that message names are properly defined
 *       in both ActivityDefinition and StructureDefinition resources</li>
 * </ul>
 *
 * <h3>Execution Listener Validation</h3>
 * <ul>
 *   <li><strong>Class Existence</strong>: Verifies that execution listener classes exist on the project classpath</li>
 *   <li><strong>Interface Implementation</strong>: Validates that execution listener classes implement
 *       the correct interface based on the API version and element type</li>
 *   <li><strong>API Version Isolation</strong>: Uses version-specific interface requirements to ensure
 *       compatibility with the correct DSF BPE API version</li>
 * </ul>
 *
 * <h3>Task Listener Validation</h3>
 * <ul>
 *   <li><strong>Class Attribute Presence</strong>: Validates that task listeners declare a class attribute</li>
 *   <li><strong>Class Existence</strong>: Verifies that task listener classes exist on the project classpath</li>
 *   <li><strong>Inheritance Validation</strong>: Validates that task listener classes extend the default
 *       superclass or implement the required interface based on the API version</li>
 * </ul>
 *
 * <h3>Timer Definition Validation</h3>
 * <ul>
 *   <li><strong>Timer Type Validation</strong>: Ensures that timer events have at least one timer type
 *       (timeDate, timeCycle, or timeDuration) defined</li>
 *   <li><strong>Placeholder Validation</strong>: Validates that timer values contain placeholders for
 *       dynamic configuration (except for fixed date/time expressions)</li>
 *   <li><strong>Fixed Date/Time Detection</strong>: Issues informational warnings for fixed date/time
 *       expressions that may not be suitable for production use</li>
 * </ul>
 *
 * <h3>Error Boundary Event Validation</h3>
 * <ul>
 *   <li><strong>Event Name Validation</strong>: Validates that error boundary events have non-empty names</li>
 *   <li><strong>Error Definition Validation</strong>: Validates that error definitions include error names
 *       and error codes</li>
 *   <li><strong>Error Code Variable Validation</strong>: Ensures that error boundary events specify
 *       an errorCodeVariable for proper error handling</li>
 * </ul>
 *
 * <h3>Conditional Event Validation</h3>
 * <ul>
 *   <li><strong>Event Name Validation</strong>: Validates that conditional intermediate catch events have names</li>
 *   <li><strong>Variable Name Validation</strong>: Ensures that conditional events specify a variable name</li>
 *   <li><strong>Variable Events Validation</strong>: Validates that variableEvents attribute is properly configured</li>
 *   <li><strong>Condition Type and Expression Validation</strong>: Validates that condition types are
 *       properly set and that condition expressions are provided when required</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * File projectRoot = new File("/path/to/project");
 * 
 * // Validate message name
 * List<BpmnElementLintItem> issues = new ArrayList<>();
 * BpmnElementLinter.checkMessageName(
 *     "myMessage", issues, "elementId", bpmnFile, "processId", projectRoot);
 * 
 * // Validate execution listener
 * BpmnElementLinter.checkExecutionListenerClasses(
 *     element, "elementId", issues, bpmnFile, "processId", projectRoot);
 * 
 * // Validate task listener
 * UserTask userTask = ...; // obtained from BPMN model
 * BpmnElementLinter.checkTaskListenerClasses(
 *     userTask, "elementId", issues, bpmnFile, "processId", projectRoot);
 * 
 * for (BpmnElementLintItem issue : issues) {
 *     System.out.println(issue.getSeverity() + ": " + issue.getMessage());
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe as it only contains static methods with no shared mutable state.
 * All methods operate on their parameters and do not maintain any internal state.
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
 * @see BpmnMessageLinter
 * @see BpmnListenerLinter
 * @see BpmnTimerLinter
 * @see BpmnEventLinter
 * @see BpmnModelLinter
 * @see BpmnElementLintItem
 * @since 1.0
 */
public class BpmnElementLinter {

    // ==================== MESSAGE NAME VALIDATION ====================

    /**
     * Checks the message name against FHIR resources.
     * <p>
     * Delegates to {@link BpmnMessageLinter#checkMessageName}.
     * </p>
     */
    public static void checkMessageName(
            String messageName,
            List<BpmnElementLintItem> issues,
            String elementId,
            File bpmnFile,
            String processId,
            File projectRoot) {
        BpmnMessageLinter.checkMessageName(messageName, issues, elementId, bpmnFile, processId, projectRoot);
    }

    // ==================== EXECUTION LISTENER VALIDATION ====================

    /**
     * Checks execution listener classes on a BPMN element with element-specific interface validation.
     * <p>
     * Delegates to {@link BpmnListenerLinter#checkExecutionListenerClasses}.
     * </p>
     */
    public static void checkExecutionListenerClasses(
            org.camunda.bpm.model.bpmn.instance.BaseElement element,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {
        BpmnListenerLinter.checkExecutionListenerClasses(element, elementId, issues, bpmnFile, processId, projectRoot);
    }

    // ==================== TASK LISTENER VALIDATION ====================

    /**
     * Checks task listener classes with VERSION-ISOLATED interface validation.
     * <p>
     * Delegates to {@link BpmnListenerLinter#checkTaskListenerClasses}.
     * </p>
     */
    public static void checkTaskListenerClasses(
            org.camunda.bpm.model.bpmn.instance.UserTask userTask,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {
        BpmnListenerLinter.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);
    }

    // ==================== TIMER DEFINITION VALIDATION ====================

    /**
     * Checks timer definition for intermediate catch events.
     * <p>
     * Delegates to {@link BpmnTimerLinter#checkTimerDefinition}.
     * </p>
     */
    public static void checkTimerDefinition(
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            org.camunda.bpm.model.bpmn.instance.TimerEventDefinition timerDef) {
        BpmnTimerLinter.checkTimerDefinition(elementId, issues, bpmnFile, processId, timerDef);
    }

    // ==================== ERROR BOUNDARY EVENT VALIDATION ====================

    /**
     * Lints a {@link org.camunda.bpm.model.bpmn.instance.BoundaryEvent} that contains an
     * {@link org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition}.
     * <p>
     * Delegates to {@link BpmnEventLinter#checkErrorBoundaryEvent}.
     * </p>
     */
    public static void checkErrorBoundaryEvent(
            org.camunda.bpm.model.bpmn.instance.BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        BpmnEventLinter.checkErrorBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
    }

    // ==================== CONDITIONAL EVENT VALIDATION ====================

    /**
     * Lints a {@link org.camunda.bpm.model.bpmn.instance.ConditionalEventDefinition} for an
     * Intermediate Catch Event.
     * <p>
     * Delegates to {@link BpmnEventLinter#checkConditionalEvent}.
     * </p>
     */
    public static void checkConditionalEvent(
            org.camunda.bpm.model.bpmn.instance.IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        BpmnEventLinter.checkConditionalEvent(catchEvent, issues, bpmnFile, processId);
    }
}