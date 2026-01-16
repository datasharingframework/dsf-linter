package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.item.BpmnElementLintItem;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Main linter class for validating Camunda BPMN models against business logic and FHIR-related constraints.
 *
 * <p>
 * The {@code BpmnModelLinter} serves as the central entry point for performing comprehensive validation
 * of BPMN 2.0 models used in Camunda workflows. It orchestrates multiple specialized sub-linters to ensure
 * that BPMN elements such as tasks, events, gateways, and subprocesses comply with naming conventions,
 * class implementation requirements, and FHIR resource referencing rules.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * This class delegates validation responsibilities to specialized linter components:
 * </p>
 * <ul>
 *   <li>{@link BpmnProcessLinter} - Validates Process-level attributes (ID pattern, count, executable, historyTimeToLive)</li>
 *   <li>{@link BpmnTaskLinter} - Validates Service Tasks, User Tasks, Send Tasks, and Receive Tasks</li>
 *   <li>{@link BpmnEventLinter} - Validates Start Events, End Events, Intermediate Events, and Boundary Events</li>
 *   <li>{@link BpmnGatewayAndFlowLinter} - Validates Exclusive Gateways, Inclusive Gateways, Event-Based Gateways, and Sequence Flows</li>
 *   <li>{@link BpmnSubProcessLinter} - Validates SubProcesses and their configurations</li>
 * </ul>
 *
 * <h2>Validation Categories</h2>
 * <p>
 * The linter performs the following categories of checks:
 * </p>
 *
 * <h3>Process Validation (DSF-Specific)</h3>
 * <p>
 * Process-level validations are delegated to {@link BpmnProcessLinter} and include:
 * </p>
 * <ul>
 *   <li><strong>Process ID Pattern</strong>: Validates that the process ID matches the DSF pattern
 *       {@code domain_processname}</li>
 *   <li><strong>Process Count</strong>: Validates that each BPMN file contains exactly one process definition</li>
 *   <li><strong>History Time To Live</strong>: Warns if {@code camunda:historyTimeToLive} is not set</li>
 *   <li><strong>Process Executable</strong>: Validates that the process has {@code isExecutable="true"}</li>
 * </ul>
 *
 * <h3>Task Validation</h3>
 * <ul>
 *   <li><strong>Service Tasks</strong>: Validates non-empty names, verifies implementation class existence,
 *       and ensures the class implements {@code org.camunda.bpm.engine.delegate.JavaDelegate}</li>
 *   <li><strong>User Tasks</strong>: Validates task names, verifies {@code formKey} format compliance,
 *       and ensures external questionnaire resources are accessible</li>
 *   <li><strong>Send Tasks</strong>: Validates message-related field injections and FHIR resource references</li>
 *   <li><strong>Receive Tasks</strong>: Validates message definitions and related configurations</li>
 * </ul>
 *
 * <h3>Event Validation</h3>
 * <ul>
 *   <li><strong>Message Events (Start/Intermediate/End)</strong>: Validates event names, verifies message definitions,
 *       checks implementation class references, and validates field injections including {@code profile},
 *       {@code messageName}, and {@code instantiatesCanonical}</li>
 *   <li><strong>Timer Events</strong>: Validates time cycle/date/duration expressions and placeholder usage</li>
 *   <li><strong>Signal Events</strong>: Validates signal definitions and references</li>
 *   <li><strong>Conditional Events</strong>: Validates condition expressions</li>
 *   <li><strong>Error Boundary Events</strong>: Validates error references, error codes, error names,
 *       and {@code errorCodeVariable} assignments</li>
 * </ul>
 *
 * <h3>Gateway and Flow Validation</h3>
 * <ul>
 *   <li><strong>Exclusive Gateways</strong>: Validates that outgoing sequence flows have appropriate names
 *       and conditions when multiple paths exist</li>
 *   <li><strong>Inclusive Gateways</strong>: Validates similar requirements as exclusive gateways</li>
 *   <li><strong>Event-Based Gateways</strong>: Validates proper configuration and outgoing flow setup</li>
 *   <li><strong>Sequence Flows</strong>: Validates naming and conditional expressions</li>
 * </ul>
 *
 * <h3>SubProcess Validation</h3>
 * <ul>
 *   <li><strong>Multi-Instance SubProcesses</strong>: Validates that multi-instance subprocesses are
 *       configured with {@code asyncBefore} set to true for proper asynchronous execution</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * File projectRoot = new File("/path/to/project");
 * BpmnModelLinter linter = new BpmnModelLinter(projectRoot);
 *
 * BpmnModelInstance model = Bpmn.readModelFromFile(new File("process.bpmn"));
 * File bpmnFile = new File("process.bpmn");
 *
 * List<BpmnElementLintItem> issues = linter.lintModel(model, bpmnFile);
 *
 * for (BpmnElementLintItem issue : issues) {
 *     System.out.println(issue.getSeverity() + ": " + issue.getMessage());
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is not thread-safe. Each linting operation creates new instances of sub-linters,
 * but shared state through the {@code projectRoot} field requires external synchronization
 * if the same instance is used across multiple threads.
 * </p>
 *
 * @see BpmnProcessLinter
 * @see BpmnTaskLinter
 * @see BpmnEventLinter
 * @see BpmnGatewayAndFlowLinter
 * @see BpmnSubProcessLinter
 * @see BpmnElementLintItem
 * @since 1.0
 */
public record BpmnModelLinter(File projectRoot) {

    /**
     * Constructs a new {@code BpmnModelLinter} instance with the specified project root directory.
     *
     * <p>
     * The project root is used by sub-linters to locate compiled classes, FHIR resources, and other
     * project artifacts required for validation. It typically points to the root directory of a Maven
     * or Gradle project containing build output directories such as {@code target/classes} or
     * {@code build/classes}.
     * </p>
     *
     * @param projectRoot the root directory of the project; must not be {@code null}
     * @throws IllegalArgumentException if {@code projectRoot} is {@code null}
     */
    public BpmnModelLinter {
        if (projectRoot == null) {
            throw new IllegalArgumentException("Project root must not be null");
        }
    }

    /**
     * Performs comprehensive validation of a BPMN model instance against business logic and FHIR-related constraints.
     *
     * <p>
     * This method serves as the main entry point for model validation. It delegates process-level validation
     * to {@link BpmnProcessLinter} and iterates through all flow elements in the provided BPMN model,
     * dispatching type-specific validation to specialized sub-linters.
     * </p>
     *
     * <h3>Validation Process</h3>
     * <ol>
     *   <li>Validates process-level attributes via {@link BpmnProcessLinter}</li>
     *   <li>Initializes specialized sub-linters for different element categories</li>
     *   <li>Iterates through all {@link FlowElement}s in the model</li>
     *   <li>Dispatches each element to the appropriate sub-linter based on its type</li>
     *   <li>Collects all validation issues in a single list</li>
     * </ol>
     *
     * <h3>Validated Element Types</h3>
     * <ul>
     *   <li><strong>Tasks</strong>: {@link ServiceTask}, {@link UserTask}, {@link SendTask}, {@link ReceiveTask}</li>
     *   <li><strong>Events</strong>: {@link StartEvent}, {@link EndEvent}, {@link IntermediateThrowEvent},
     *       {@link IntermediateCatchEvent}, {@link BoundaryEvent}</li>
     *   <li><strong>Gateways</strong>: {@link ExclusiveGateway}, {@link InclusiveGateway}, {@link EventBasedGateway}</li>
     *   <li><strong>Flows</strong>: {@link SequenceFlow}</li>
     *   <li><strong>Subprocesses</strong>: {@link SubProcess}</li>
     * </ul>
     *
     * @param model    the BPMN model instance to validate; must not be {@code null}
     * @param bpmnFile the source BPMN file being validated; used for issue location reporting in lint items.
     *                 Must not be {@code null}
     * @return an immutable list of {@link BpmnElementLintItem} objects representing all validation issues found.
     * Returns an empty list if no issues are detected. Never returns {@code null}
     * @see BpmnProcessLinter
     * @see BpmnTaskLinter
     * @see BpmnEventLinter
     * @see BpmnGatewayAndFlowLinter
     * @see BpmnSubProcessLinter
     */
    public List<BpmnElementLintItem> lintModel(BpmnModelInstance model, File bpmnFile) {
        List<BpmnElementLintItem> issues = new ArrayList<>();
        Collection<FlowElement> flowElements = model.getModelElementsByType(FlowElement.class);

        // Initialize sub-linters
        BpmnProcessLinter processLinter = new BpmnProcessLinter(projectRoot);
        BpmnTaskLinter taskLinter = new BpmnTaskLinter(projectRoot);
        BpmnEventLinter eventLinter = new BpmnEventLinter(projectRoot);
        BpmnGatewayAndFlowLinter gatewayAndFlowLinter = new BpmnGatewayAndFlowLinter(projectRoot);
        BpmnSubProcessLinter subProcessLinter = new BpmnSubProcessLinter(projectRoot);

        // Validate process-level attributes and extract process ID
        String processId = processLinter.lintProcesses(model, bpmnFile, issues);

        // Validate flow elements
        for (FlowElement element : flowElements) {
            lintFlowElement(element, issues, bpmnFile, processId,
                    taskLinter, eventLinter, gatewayAndFlowLinter, subProcessLinter);
        }

        return issues;
    }

    /**
     * Dispatches a flow element to the appropriate sub-linter based on its type.
     *
     * @param element             the flow element to validate
     * @param issues              the list to add validation issues to
     * @param bpmnFile            the source BPMN file
     * @param processId           the process ID for reference
     * @param taskLinter          the task linter instance
     * @param eventLinter         the event linter instance
     * @param gatewayAndFlowLinter the gateway and flow linter instance
     * @param subProcessLinter    the subprocess linter instance
     */
    private void lintFlowElement(
            FlowElement element,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            BpmnTaskLinter taskLinter,
            BpmnEventLinter eventLinter,
            BpmnGatewayAndFlowLinter gatewayAndFlowLinter,
            BpmnSubProcessLinter subProcessLinter) {

        switch (element) {
            case ServiceTask serviceTask ->
                taskLinter.lintServiceTask(serviceTask, issues, bpmnFile, processId);
            case StartEvent startEvent ->
                eventLinter.lintStartEvent(startEvent, issues, bpmnFile, processId);
            case IntermediateThrowEvent throwEvent ->
                eventLinter.lintIntermediateThrowEvent(throwEvent, issues, bpmnFile, processId);
            case EndEvent endEvent ->
                eventLinter.lintEndEvent(endEvent, issues, bpmnFile, processId);
            case IntermediateCatchEvent catchEvent ->
                eventLinter.lintIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
            case BoundaryEvent boundaryEvent ->
                eventLinter.lintBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
            case ExclusiveGateway exclusiveGateway ->
                gatewayAndFlowLinter.lintExclusiveGateway(exclusiveGateway, issues, bpmnFile, processId);
            case InclusiveGateway inclusiveGateway ->
                gatewayAndFlowLinter.lintInclusiveGateway(inclusiveGateway, issues, bpmnFile, processId);
            case SequenceFlow sequenceFlow ->
                gatewayAndFlowLinter.lintSequenceFlow(sequenceFlow, issues, bpmnFile, processId);
            case UserTask userTask ->
                taskLinter.lintUserTask(userTask, issues, bpmnFile, processId);
            case SendTask sendTask ->
                taskLinter.lintSendTask(sendTask, issues, bpmnFile, processId);
            case ReceiveTask receiveTask ->
                taskLinter.lintReceiveTask(receiveTask, issues, bpmnFile, processId);
            case SubProcess subProcess ->
                subProcessLinter.lintSubProcess(subProcess, issues, bpmnFile, processId);
            case EventBasedGateway gateway ->
                gatewayAndFlowLinter.lintEventBasedGateway(gateway, issues, bpmnFile, processId);
            default -> {
                // Element type not specifically handled
            }
        }
    }
}
