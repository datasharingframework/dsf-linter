package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.item.BpmnElementLintItem;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.Process;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * The {@code BpmnModelLinter} is the main entry point for linting Camunda BPMN models
 * against various business logic and FHIR-related constraints. It ensures that BPMN elements
 * such as Tasks, Events, Gateways, and others comply with specific naming, class-implementation,
 * and FHIR resource referencing rules.
 * </p>
 *
 * <h2>Overview of linting Checks</h2>
 * <ul>
 *   <li><strong>Service Tasks</strong>: Checks non-empty name, implementation class existence,
 *       and {@code JavaDelegate} implementation.</li>
 *   <li><strong>Message Start/Intermediate/End Events</strong>:
 *       Ensures non-empty names, verifies corresponding message definitions, checks
 *       implementation class references, and lints field injections (e.g., {@code profile},
 *       {@code messageName}, {@code instantiatesCanonical}).</li>
 *   <li><strong>Error Boundary Events</strong>:
 *       Splits linting into two scenarios (with or without {@code errorRef}).
 *       Logs WARN if boundary name is empty, ERROR if error name/code is missing, and WARN
 *       if {@code errorCodeVariable} is empty.</li>
 *   <li><strong>Exclusive Gateways and Sequence Flows</strong>:
 *       If an ExclusiveGateway has multiple outgoing flows, warns if the Sequence Flow has no name
 *       and errors if no condition is present on non-default flows.</li>
 *   <li><strong>User Tasks</strong>:
 *       Checks that the user task name is not empty, verifies {@code formKey} format,
 *       and ensures the external questionnaire resource is findable.</li>
 *   <li><strong>Timer/Signal/Conditional Events</strong>:
 *       lints presence of expected configuration (time expressions, signal definitions,
 *       condition expressions) and warns if certain placeholders or parameters are missing.</li>
 *   <li><strong>SubProcesses</strong>:
 *       If multi-instance, checks if the sub-process is configured with {@code asyncBefore}.</li>
 *   <li><strong>Field Injections</strong>:
 *       For BPMN elements that utilize {@code <camunda:field>} (e.g., SendTask, Message Throw/End Events),
 *       checks:
 *       <ul>
 *         <li>{@code profile}: Non-empty, optional version placeholder, existence in FHIR StructureDefinition.</li>
 *         <li>{@code messageName}: Non-empty, optional cross-check with the previously found {@code profile} string.</li>
 *         <li>{@code instantiatesCanonical}: Non-empty, warns if missing version placeholder, checks existence in
 *         FHIR ActivityDefinition, and cross-checks message name if found.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>
 * References:
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">Camunda Extension Elements</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 * </p>
 */
public class BpmnModelLinter {

    private final File projectRoot;

    /**
     * Constructs a new {@code BpmnModelLinter} with the specified project root.
     *
     * @param projectRoot the root directory of the project (e.g., containing {@code target/classes} or {@code build/classes})
     * @throws IllegalArgumentException if {@code projectRoot} is null
     */
    public BpmnModelLinter(File projectRoot) {
        if (projectRoot == null) {
            throw new IllegalArgumentException("Project root must not be null");
        }
        this.projectRoot = projectRoot;
    }

    /**
     * <p>
     * lints a given {@link BpmnModelInstance} against various business and
     * FHIR-related constraints, collecting any violations in a list of
     * {@link BpmnElementLintItem}. Each item denotes a specific issue
     * (e.g., empty name, missing field injection, invalid reference).
     * </p>
     *
     * <p>
     * The checks performed include (but are not limited to):
     * </p>
     * <ul>
     *   <li>ServiceTask: name not empty, class must exist, class must implement
     *       {@code JavaDelegate}.</li>
     *   <li>Message Events: checks name, message definitions, and the correctness
     *       of field injections such as {@code profile}, {@code messageName}, and
     *       {@code instantiatesCanonical} (including references to FHIR
     *       StructureDefinition and ActivityDefinition).</li>
     *   <li>Error Boundary Events: if {@code errorRef} is present, check that
     *       error name/code is not empty; if boundary name is empty => WARN, and
     *       {@code errorCodeVariable} missing => WARN.</li>
     *   <li>Exclusive Gateway / Sequence Flow: if the gateway has more than one
     *       outgoing flow, warns if a flow is unnamed, errors if no condition
     *       on non-default flows.</li>
     *   <li>Various other events (Timer, Signal, Conditional) each have specialized
     *       checks for completeness and placeholder usage.</li>
     * </ul>
     *
     * @param model
     *         the BPMN model to lint
     * @param bpmnFile
     *         the source {@code .bpmn} file (used only for logging in the linting items)
     * @return a list of linting items representing all discovered issues
     */
    public List<BpmnElementLintItem> lintModel(
            BpmnModelInstance model,
            File bpmnFile) {

        List<BpmnElementLintItem> issues = new ArrayList<>();
        Collection<FlowElement> flowElements = model.getModelElementsByType(FlowElement.class);

        // The processId is now extracted internally, simplifying the method call.
        String processId = extractProcessId(model);

        // Initialize sub-linters with the project root
        BpmnTaskLinter taskLinter = new BpmnTaskLinter(projectRoot);
        BpmnEventLinter eventLinter = new BpmnEventLinter(projectRoot);
        BpmnGatewayAndFlowLinter gatewayAndFlowLinter = new BpmnGatewayAndFlowLinter(projectRoot);
        BpmnSubProcessLinter subProcessLinter = new BpmnSubProcessLinter(projectRoot);

        for (FlowElement element : flowElements) {
            // --- SERVICE TASK ---
            if (element instanceof ServiceTask serviceTask) {
                taskLinter.lintServiceTask(serviceTask, issues, bpmnFile, processId);
            }
            // --- START EVENT ---
            else if (element instanceof StartEvent startEvent) {
                eventLinter.lintStartEvent(startEvent, issues, bpmnFile, processId);
            }
            // --- INTERMEDIATE THROW EVENT ---
            else if (element instanceof IntermediateThrowEvent throwEvent) {
                eventLinter.lintIntermediateThrowEvent(throwEvent, issues, bpmnFile, processId);
            }
            // --- END EVENT ---
            else if (element instanceof EndEvent endEvent) {
                eventLinter.lintEndEvent(endEvent, issues, bpmnFile, processId);
            }
            // --- INTERMEDIATE CATCH EVENT ---
            else if (element instanceof IntermediateCatchEvent catchEvent) {
                eventLinter.lintIntermediateCatchEvent(catchEvent, issues, bpmnFile, processId);
            }
            // --- BOUNDARY EVENT ---
            else if (element instanceof BoundaryEvent boundaryEvent) {
                eventLinter.lintBoundaryEvent(boundaryEvent, issues, bpmnFile, processId);
            }
            // --- EXCLUSIVE GATEWAY ---
            else if (element instanceof ExclusiveGateway exclusiveGateway) {
                gatewayAndFlowLinter.lintExclusiveGateway(exclusiveGateway, issues, bpmnFile, processId);
            }
            // --- INCLUSIVE GATEWAY ---
            else if (element instanceof InclusiveGateway inclusiveGateway) {
                gatewayAndFlowLinter.lintInclusiveGateway(inclusiveGateway, issues, bpmnFile, processId);
            }
            // --- SEQUENCE FLOW ---
            else if (element instanceof SequenceFlow sequenceFlow) {
                gatewayAndFlowLinter.lintSequenceFlow(sequenceFlow, issues, bpmnFile, processId);
            }
            // --- USER TASK ---
            else if (element instanceof UserTask userTask) {
                taskLinter.lintUserTask(userTask, issues, bpmnFile, processId);
            }
            // --- SEND TASK ---
            else if (element instanceof SendTask sendTask) {
                taskLinter.lintSendTask(sendTask, issues, bpmnFile, processId);
            }
            // --- RECEIVE TASK ---
            else if (element instanceof ReceiveTask receiveTask) {
                taskLinter.lintReceiveTask(receiveTask, issues, bpmnFile, processId);
            }
            // --- SUB PROCESS ---
            else if (element instanceof SubProcess subProcess) {
                subProcessLinter.lintSubProcess(subProcess, issues, bpmnFile, processId);
            }
            // --- EVENT-BASED GATEWAY ---
            else if (element instanceof EventBasedGateway gateway) {
                gatewayAndFlowLinter.lintEventBasedGateway(gateway, issues, bpmnFile, processId);
            }
        }
        return issues;
    }

    /**
     * Extracts the ID from the first process definition found in the model.
     *
     * @param model The BpmnModelInstance to search within.
     * @return The process ID as a string, or an empty string if not found.
     */
    private String extractProcessId(BpmnModelInstance model) {
        return model.getModelElementsByType(Process.class)
                .stream()
                .map(Process::getId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }
}

