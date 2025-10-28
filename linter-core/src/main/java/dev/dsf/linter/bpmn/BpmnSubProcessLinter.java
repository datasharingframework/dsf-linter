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
 * This linter checks specific properties of a subprocess element, particularly focusing on scenarios
 * where the subprocess is configured with multi-instance loop characteristics. In such cases, the linter
 * ensures that the multi-instance configuration has the {@code camunda:asyncBefore} attribute set to {@code true}.
 * This requirement helps to enforce proper asynchronous execution for multi-instance subprocesses.
 * </p>
 *
 * <p>
 * References:
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">
 *       Camunda Extension Elements</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 * </p>
 */
public class BpmnSubProcessLinter
{
    @SuppressWarnings("unused")
    private final File projectRoot;

    /**
     * Constructs a new {@code BpmnSubProcessLinter} with the specified project root.
     * <p>
     * The project root is required to locate necessary resources and dependencies during linting.
     * </p>
     *
     * @param projectRoot the root directory of the project containing relevant classes and resources
     */
    public BpmnSubProcessLinter(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    /**
     * lints a given {@link SubProcess} element.
     * <p>
     * This method checks if the subprocess is configured with multi-instance loop characteristics.
     * If the subprocess has multi-instance settings, it enforces that the {@code camunda:asyncBefore}
     * attribute is set to {@code true}. If this condition is not met, a lint issue is added;
     * otherwise, a success item is recorded.
     * </p>
     *
     * @param subProcess the {@link SubProcess} element to lint
     * @param issues a list of {@link BpmnElementLintItem} where any detected lint issues or success items will be added
     * @param bpmnFile the BPMN file under linting (used for logging and reference purposes)
     * @param processId the identifier of the BPMN process containing the subprocess
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
