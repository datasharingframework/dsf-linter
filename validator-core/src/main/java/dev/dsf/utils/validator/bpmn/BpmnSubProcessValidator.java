package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.FloatingElementType;
import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;
import dev.dsf.utils.validator.item.BpmnFloatingElementValidationItem;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;
import java.util.List;

/**
 * The {@code BpmnSubProcessValidator} class provides validation logic for BPMN {@link SubProcess} elements.
 * <p>
 * This validator checks specific properties of a subprocess element, particularly focusing on scenarios
 * where the subprocess is configured with multi-instance loop characteristics. In such cases, the validator
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
public class BpmnSubProcessValidator
{
    private final File projectRoot;

    /**
     * Constructs a new {@code BpmnSubProcessValidator} with the specified project root.
     * <p>
     * The project root is required to locate necessary resources and dependencies during validation.
     * </p>
     *
     * @param projectRoot the root directory of the project containing relevant classes and resources
     */
    public BpmnSubProcessValidator(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    /**
     * Validates a given {@link SubProcess} element.
     * <p>
     * This method checks if the subprocess is configured with multi-instance loop characteristics.
     * If the subprocess has multi-instance settings, it enforces that the {@code camunda:asyncBefore}
     * attribute is set to {@code true}. If this condition is not met, a validation issue is added to the
     * provided list of {@link BpmnElementValidationItem}.
     * </p>
     *
     * @param subProcess the {@link SubProcess} element to validate
     * @param issues a list of {@link BpmnElementValidationItem} where any detected validation issues will be added
     * @param bpmnFile the BPMN file under validation (used for logging and reference purposes)
     * @param processId the identifier of the BPMN process containing the subprocess
     */
    public void validateSubProcess(
            SubProcess subProcess,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = subProcess.getId();

        if (subProcess.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics multi)
        {
            if (!multi.isCamundaAsyncBefore())
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "SubProcess has multi-instance but is not asyncBefore=true",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.WARN,
                        FloatingElementType.SUB_PROCESS_HAS_MULTI_INSTANCE_BUT_IS_NOT_ASYNC_BEFORE_TRUE
                ));
            }
        }
    }
}
