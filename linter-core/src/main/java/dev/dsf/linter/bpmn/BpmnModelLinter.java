package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.BpmnElementLintItem;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.Process;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

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
 *       and {@code errorCodeVariable} assignments. Issues warnings for missing names and errors for
 *       missing critical error configuration</li>
 * </ul>
 *
 * <h3>Gateway and Flow Validation</h3>
 * <ul>
 *   <li><strong>Exclusive Gateways</strong>: Validates that outgoing sequence flows have appropriate names
 *       and conditions when multiple paths exist</li>
 *   <li><strong>Inclusive Gateways</strong>: Validates similar requirements as exclusive gateways</li>
 *   <li><strong>Event-Based Gateways</strong>: Validates proper configuration and outgoing flow setup</li>
 *   <li><strong>Sequence Flows</strong>: Validates naming and conditional expressions, particularly for
 *       non-default flows from splitting gateways</li>
 * </ul>
 *
 * <h3>SubProcess Validation</h3>
 * <ul>
 *   <li><strong>Multi-Instance SubProcesses</strong>: Validates that multi-instance subprocesses are
 *       configured with {@code asyncBefore} set to true for proper asynchronous execution</li>
 * </ul>
 *
 * <h3>FHIR-Specific Validation</h3>
 * <p>
 * For BPMN elements utilizing Camunda field injections (e.g., {@code <camunda:field>}), the following
 * FHIR-related validations are performed:
 * </p>
 * <ul>
 *   <li><strong>profile</strong>: Validates non-empty value, checks for version placeholders,
 *       and verifies existence in FHIR StructureDefinition resources</li>
 *   <li><strong>messageName</strong>: Validates non-empty value and performs cross-validation with
 *       the associated {@code profile} reference when applicable</li>
 *   <li><strong>instantiatesCanonical</strong>: Validates non-empty value, warns if version placeholder
 *       is missing, verifies existence in FHIR ActivityDefinition resources, and cross-validates with
 *       message name references</li>
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
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">Camunda Extension Elements</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 *
 * @see BpmnTaskLinter
 * @see BpmnEventLinter
 * @see BpmnGatewayAndFlowLinter
 * @see BpmnSubProcessLinter
 * @see BpmnElementLintItem
 * @since 1.0
 */
public record BpmnModelLinter(File projectRoot) {

    /**
     * Pattern for validating BPMN Process IDs.
     * <p>
     * The Process ID must follow the format: {@code domain_processname}
     * where both domain and processname consist only of alphanumeric characters and hyphens.
     * </p>
     * <p>
     * Examples of valid IDs:
     * <ul>
     *   <li>{@code testorg_myprocess}</li>
     *   <li>{@code dsf-dev_download-allowlist}</li>
     * </ul>
     * </p>
     *
     * @see <a href="https://github.com/datasharingframework/dsf">DSF Framework</a>
     */
    private static final String PROCESS_ID_PATTERN_STRING = "^(?<domainNoDots>[a-zA-Z0-9-]+)_(?<processName>[a-zA-Z0-9-]+)$";
    private static final Pattern PROCESS_ID_PATTERN = Pattern.compile(PROCESS_ID_PATTERN_STRING);

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
     * This method serves as the main entry point for model validation. It iterates through all flow elements
     * in the provided BPMN model and delegates type-specific validation to specialized sub-linters. The method
     * collects all validation issues and returns them as a list of {@link BpmnElementLintItem} objects, where
     * each item represents a specific violation with details about its location, severity, and description.
     * </p>
     *
     * <h3>Validation Process</h3>
     * <p>
     * The method performs the following steps:
     * </p>
     * <ol>
     *   <li>Extracts the process ID from the BPMN model for reference in lint items</li>
     *   <li>Initializes specialized sub-linters for different element categories</li>
     *   <li>Iterates through all {@link FlowElement}s in the model</li>
     *   <li>Dispatches each element to the appropriate sub-linter based on its type</li>
     *   <li>Collects all validation issues in a single list</li>
     * </ol>
     *
     * <h3>Validated Element Types</h3>
     * <p>
     * The following BPMN element types are validated:
     * </p>
     * <ul>
     *   <li><strong>Tasks</strong>: {@link ServiceTask}, {@link UserTask}, {@link SendTask}, {@link ReceiveTask}</li>
     *   <li><strong>Events</strong>: {@link StartEvent}, {@link EndEvent}, {@link IntermediateThrowEvent},
     *       {@link IntermediateCatchEvent}, {@link BoundaryEvent}</li>
     *   <li><strong>Gateways</strong>: {@link ExclusiveGateway}, {@link InclusiveGateway}, {@link EventBasedGateway}</li>
     *   <li><strong>Flows</strong>: {@link SequenceFlow}</li>
     *   <li><strong>Subprocesses</strong>: {@link SubProcess}</li>
     * </ul>
     *
     * <h3>Validation Categories</h3>
     * <p>
     * Each element type is validated for:
     * </p>
     * <ul>
     *   <li><strong>Naming</strong>: Ensures elements have non-empty, meaningful names</li>
     *   <li><strong>Implementation</strong>: Verifies that implementation classes exist and implement required interfaces</li>
     *   <li><strong>Configuration</strong>: Checks that required attributes and properties are properly configured</li>
     *   <li><strong>FHIR Resources</strong>: Validates references to FHIR StructureDefinitions, ActivityDefinitions,
     *       and Questionnaires</li>
     *   <li><strong>Field Injections</strong>: Validates Camunda field injection values for message-related elements</li>
     *   <li><strong>Expressions</strong>: Validates conditional expressions, timer expressions, and other dynamic content</li>
     * </ul>
     *
     * <h3>Issue Severity Levels</h3>
     * <p>
     * Validation issues are categorized by severity:
     * </p>
     * <ul>
     *   <li><strong>ERROR</strong>: Critical issues that will likely cause runtime failures or incorrect behavior</li>
     *   <li><strong>WARNING</strong>: Non-critical issues that may indicate poor practices or potential problems</li>
     *   <li><strong>INFO</strong>: Informational messages about the model structure or recommendations</li>
     * </ul>
     *
     * @param model    the BPMN model instance to validate; must not be {@code null}
     * @param bpmnFile the source BPMN file being validated; used for issue location reporting in lint items.
     *                 Must not be {@code null}
     * @return an immutable list of {@link BpmnElementLintItem} objects representing all validation issues found.
     * Returns an empty list if no issues are detected. Never returns {@code null}
     * @see BpmnTaskLinter
     * @see BpmnEventLinter
     * @see BpmnGatewayAndFlowLinter
     * @see BpmnSubProcessLinter
     * @see BpmnElementLintItem
     */
    public List<BpmnElementLintItem> lintModel(
            BpmnModelInstance model,
            File bpmnFile) {

        List<BpmnElementLintItem> issues = new ArrayList<>();
        Collection<FlowElement> flowElements = model.getModelElementsByType(FlowElement.class);

        // The processId is now extracted internally, simplifying the method call.
        String processId = extractProcessId(model);

        // Validate Process ID pattern
        validateProcessIdPattern(processId, bpmnFile, issues);

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
     * Extracts the process ID from the first process definition found in the BPMN model.
     *
     * <p>
     * This method searches the BPMN model for {@link Process} elements and returns the ID of the first
     * process found. The process ID is used throughout the linting process for issue identification and
     * reporting. If the model contains multiple process definitions, only the first one is considered.
     * </p>
     *
     * <p>
     * This is a utility method that simplifies process identification and ensures consistent process
     * ID extraction across all validation operations.
     * </p>
     *
     * @param model the BPMN model instance to extract the process ID from; must not be {@code null}
     * @return the process ID as a {@link String}, or an empty string if no process is found or if
     * the process ID is {@code null}. Never returns {@code null}
     */
    private String extractProcessId(BpmnModelInstance model) {
        return model.getModelElementsByType(Process.class)
                .stream()
                .map(Process::getId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }

    /**
     * Validates that the BPMN Process ID matches the required DSF pattern.
     *
     * <p>
     * The Process ID must follow the pattern: {@code domain_processname}
     * where both domain and processname consist only of alphanumeric characters and hyphens.
     * </p>
     *
     * <p>
     * This validation is based on the DSF Framework requirement defined in:
     * {@code dsf-bpe/dsf-bpe-process-api/src/main/java/dev/dsf/bpe/api/plugin/AbstractProcessPlugin.java}
     * </p>
     *
     * @param processId the process ID to validate
     * @param bpmnFile  the BPMN file for error reporting
     * @param issues    the list to add validation issues to
     */
    private void validateProcessIdPattern(String processId, File bpmnFile, List<BpmnElementLintItem> issues) {
        if (processId == null || processId.isEmpty()) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR,
                    LintingType.BPMN_PROCESS_ID_EMPTY,
                    "Process",
                    bpmnFile,
                    "",
                    "BPMN Process ID is empty or not defined."
            ));
            return;
        }

        if (!PROCESS_ID_PATTERN.matcher(processId).matches()) {
            String description = String.format(
                    "Process ID '%s' does not match the required pattern '%s'. " +
                    "Expected format: domain_processname (e.g., testorg_myprocess, dsf-dev_download-allowlist).",
                    processId,
                    PROCESS_ID_PATTERN_STRING
            );
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR,
                    LintingType.BPMN_PROCESS_ID_PATTERN_MISMATCH,
                    processId,
                    bpmnFile,
                    processId,
                    description
            ));
        } else {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.SUCCESS,
                    LintingType.SUCCESS,
                    processId,
                    bpmnFile,
                    processId,
                    String.format("Process ID '%s' matches the required pattern.", processId)
            ));
        }
    }
}

