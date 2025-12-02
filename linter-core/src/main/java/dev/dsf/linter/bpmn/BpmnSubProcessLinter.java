package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.item.BpmnElementLintItem;
import dev.dsf.linter.output.item.BpmnElementLintItemSuccess;
import dev.dsf.linter.output.item.BpmnSubProcessHasMultiInstanceButIsNotAsyncBeforeTrueLintItem;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import org.camunda.bpm.model.bpmn.instance.SubProcess;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkExecutionListenerClasses;

/**
 * Specialized linter class for validating BPMN subprocess elements against business logic and best practices.
 *
 * <p>
 * The {@code BpmnSubProcessLinter} provides comprehensive validation for BPMN 2.0 subprocess elements
 * used in Camunda workflows. It ensures that subprocesses, particularly multi-instance subprocesses,
 * are properly configured for asynchronous execution and that execution listeners are correctly implemented.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * This class is a specialized sub-linter that is invoked by the {@link BpmnModelLinter} during the validation
 * of a complete BPMN model. It focuses exclusively on subprocess-related validations, delegating execution
 * listener class validation to shared utility methods from {@link BpmnElementLinter}.
 * </p>
 *
 * <h2>Validation Categories</h2>
 * <p>
 * The linter performs the following categories of checks:
 * </p>
 *
 * <h3>Multi-Instance SubProcess Validation</h3>
 * <ul>
 *   <li><strong>AsyncBefore Configuration</strong>: Validates that multi-instance subprocesses are configured
 *       with {@code asyncBefore=true}. This is critical for proper asynchronous execution of multi-instance
 *       loops, ensuring that each instance can be processed independently without blocking the process engine.
 *       Issues an error if a multi-instance subprocess does not have {@code asyncBefore} set to {@code true}.</li>
 * </ul>
 *
 * <h3>Execution Listener Validation</h3>
 * <ul>
 *   <li><strong>Execution Listener Classes</strong>: Validates that execution listener classes referenced
 *       in subprocess elements exist in the project and implement the required Camunda interfaces
 *       (e.g., {@code org.camunda.bpm.engine.delegate.ExecutionListener}).</li>
 * </ul>
 *
 * <h2>Validation Logic</h2>
 * <p>
 * The linter applies the following validation rules:
 * </p>
 * <ul>
 *   <li>Multi-instance validation is only performed when a subprocess has loop characteristics of type
 *       {@link MultiInstanceLoopCharacteristics}. Standard subprocesses without multi-instance configuration
 *       are not subject to this validation.</li>
 *   <li>The {@code asyncBefore} check is critical for multi-instance subprocesses because without it,
 *       the process engine may not properly handle concurrent execution of multiple instances, leading
 *       to potential performance issues or incorrect process behavior.</li>
 *   <li>Execution listener validation is performed for all subprocesses, regardless of whether they
 *       are configured as multi-instance or standard subprocesses.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * File projectRoot = new File("/path/to/project");
 * BpmnSubProcessLinter linter = new BpmnSubProcessLinter(projectRoot);
 *
 * List<BpmnElementLintItem> issues = new ArrayList<>();
 * File bpmnFile = new File("process.bpmn");
 * String processId = "myProcess";
 *
 * SubProcess subProcess = // ... obtain subprocess from model
 * linter.lintSubProcess(subProcess, issues, bpmnFile, processId);
 *
 * for (BpmnElementLintItem issue : issues) {
 *     System.out.println(issue.getSeverity() + ": " + issue.getMessage());
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is not thread-safe. Each linting operation modifies the provided {@code issues} list,
 * and the shared {@code projectRoot} field requires external synchronization if the same instance
 * is used across multiple threads.
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/bpmn-model/subprocesses/">Camunda SubProcesses</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/bpmn-model/multi-instance/">Camunda Multi-Instance</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/async-execution/">Camunda Asynchronous Execution</a></li>
 * </ul>
 *
 * @see BpmnModelLinter
 * @see BpmnElementLinter
 * @see BpmnElementLintItem
 * @see SubProcess
 * @see MultiInstanceLoopCharacteristics
 * @since 1.0
 */
public record BpmnSubProcessLinter(File projectRoot) {

    /**
     * Lints a given {@link SubProcess} element and adds the results to the issues list.
     */
    public void lintSubProcess(
            SubProcess subProcess,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = subProcess.getId();

        // 1. Check multi-instance async before
        if (subProcess.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics multi) {
            if (!multi.isCamundaAsyncBefore()) {
                issues.add(new BpmnSubProcessHasMultiInstanceButIsNotAsyncBeforeTrueLintItem(
                        elementId, bpmnFile, processId));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "SubProcess with multi-instance loop characteristics is correctly configured with asyncBefore=true"
                ));
            }
        }

        checkExecutionListenerClasses(subProcess, elementId, issues, bpmnFile, processId, projectRoot);
    }
}