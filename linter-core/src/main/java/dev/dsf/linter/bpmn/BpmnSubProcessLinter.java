package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.FloatingElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.BpmnElementLintItem;
import dev.dsf.linter.output.item.BpmnElementLintItemSuccess;
import dev.dsf.linter.output.item.BpmnFloatingElementLintItem;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import dev.dsf.linter.output.LintingType;

import java.io.File;
import java.util.List;

/**
 * The {@code BpmnSubProcessLinter} class provides linter logic for BPMN {@link SubProcess} elements.
 * <p>
 * This linter is responsible for validating BPMN subprocess configurations and ensuring compliance with
 * best practices for process execution. It performs targeted checks on subprocess elements, particularly
 * focusing on scenarios where the subprocess is configured with multi-instance loop characteristics.
 * </p>
 *
 * <h2>Validation Rules</h2>
 * <p>
 * The linter enforces the following rules:
 * </p>
 * <ul>
 *   <li><b>Multi-Instance Async Before Check:</b> When a subprocess has multi-instance loop characteristics,
 *       it must have the {@code camunda:asyncBefore} attribute set to {@code true}. This ensures proper
 *       asynchronous execution and prevents potential performance issues or deadlocks in multi-instance scenarios.</li>
 * </ul>
 *
 * <h2>Lint Results</h2>
 * <p>
 * The linter produces different types of results:
 * </p>
 * <ul>
 *   <li>{@link BpmnFloatingElementLintItem} with severity {@link LinterSeverity#WARN} when a multi-instance
 *       subprocess does not have {@code asyncBefore=true}</li>
 *   <li>{@link BpmnElementLintItemSuccess} when a multi-instance subprocess is correctly configured</li>
 *   <li>No output for subprocesses without multi-instance characteristics</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * File projectRoot = new File("/path/to/project");
 * BpmnSubProcessLinter linter = new BpmnSubProcessLinter(projectRoot);
 * 
 * List<BpmnElementLintItem> issues = new ArrayList<>();
 * SubProcess subProcess = ...; // obtained from BPMN model
 * File bpmnFile = new File("process.bpmn");
 * String processId = "myProcess";
 * 
 * linter.lintSubProcess(subProcess, issues, bpmnFile, processId);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe and can be reused across multiple linting operations.
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">
 *       Camunda Extension Elements</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 *
 * @see SubProcess
 * @see MultiInstanceLoopCharacteristics
 * @see BpmnElementLintItem
 * @see BpmnFloatingElementLintItem
 * @see BpmnElementLintItemSuccess
 * @since 1.0
 */
public class BpmnSubProcessLinter
{
    @SuppressWarnings("unused")
    private final File projectRoot;

    /**
     * Constructs a new {@code BpmnSubProcessLinter} with the specified project root.
     * <p>
     * The project root is required to locate necessary resources and dependencies during linting.
     * This constructor initializes the linter with the project context, allowing it to access
     * project-specific configurations and resources if needed for future extensions.
     * </p>
     *
     * @param projectRoot the root directory of the project containing relevant classes and resources;
     *                    must not be {@code null}
     * @throws NullPointerException if {@code projectRoot} is {@code null}
     */
    public BpmnSubProcessLinter(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    /**
     * Lints a given {@link SubProcess} element and adds the results to the issues list.
     * <p>
     * This method performs validation checks on the subprocess element, specifically targeting
     * multi-instance configurations. The validation process follows these steps:
     * </p>
     * <ol>
     *   <li>Retrieves the element ID from the subprocess</li>
     *   <li>Checks if the subprocess has {@link MultiInstanceLoopCharacteristics}</li>
     *   <li>If multi-instance is detected, validates that {@code camunda:asyncBefore} is set to {@code true}</li>
     *   <li>Adds a warning ({@link BpmnFloatingElementLintItem}) if validation fails</li>
     *   <li>Adds a success item ({@link BpmnElementLintItemSuccess}) if validation passes</li>
     *   <li>Takes no action if the subprocess does not have multi-instance characteristics</li>
     * </ol>
     *
     * <h3>Multi-Instance Async Before Requirement</h3>
     * <p>
     * The {@code asyncBefore=true} attribute is crucial for multi-instance subprocesses because:
     * </p>
     * <ul>
     *   <li>It ensures each instance is executed asynchronously via the job executor</li>
     *   <li>It prevents potential transaction boundary issues</li>
     *   <li>It improves scalability and prevents deadlocks in complex process scenarios</li>
     *   <li>It allows better control over multi-instance execution flow</li>
     * </ul>
     *
     * <h3>Lint Item Types</h3>
     * <p>
     * This method may add the following types of items to the issues list:
     * </p>
     * <ul>
     *   <li><b>Warning:</b> {@link BpmnFloatingElementLintItem} with type
     *       {@link FloatingElementType#SUB_PROCESS_HAS_MULTI_INSTANCE_BUT_IS_NOT_ASYNC_BEFORE_TRUE}
     *       and severity {@link LinterSeverity#WARN}</li>
     *   <li><b>Success:</b> {@link BpmnElementLintItemSuccess} when the subprocess is correctly configured</li>
     * </ul>
     *
     * @param subProcess the {@link SubProcess} element to lint; must not be {@code null}
     * @param issues a mutable list of {@link BpmnElementLintItem} where any detected lint issues or 
     *               success items will be added; must not be {@code null}
     * @param bpmnFile the BPMN file under linting, used for logging and reference purposes in lint items;
     *                 must not be {@code null}
     * @param processId the identifier of the BPMN process containing the subprocess; 
     *                  must not be {@code null} or empty
     * @throws NullPointerException if any parameter is {@code null}
     */
    public void lintSubProcess(
            SubProcess subProcess,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = subProcess.getId();

        if (subProcess.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics multi)
        {
            if (!multi.isCamundaAsyncBefore())
            {
                issues.add(new BpmnFloatingElementLintItem(
                        elementId, bpmnFile, processId,
                        "SubProcess has multi-instance but is not asyncBefore=true",
                        LintingType.BPMN_FLOATING_ELEMENT,
                        LinterSeverity.WARN,
                        FloatingElementType.SUB_PROCESS_HAS_MULTI_INSTANCE_BUT_IS_NOT_ASYNC_BEFORE_TRUE
                ));
            }
            else
            {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "SubProcess with multi-instance loop characteristics is correctly configured with asyncBefore=true"
                ));
            }
        }
    }

}

