package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.item.BpmnElementValidationItem;
import dev.dsf.utils.validator.item.BpmnFloatingElementValidationItem;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import dev.dsf.utils.validator.ValidationType;

import java.io.File;
import java.util.List;

/**
 * <p>
 * The {@code BpmnSubProcessValidator} class handles validation logic for {@link SubProcess} elements.
 * </p>
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
public class BpmnSubProcessValidator
{
    private final File projectRoot;

    public BpmnSubProcessValidator(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    /**
     * Validates a {@link SubProcess}, checking if it has multi-instance loop
     * characteristics and if so, enforcing {@code camunda:asyncBefore=true}.
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
                        ValidationType.BPMN_FLOATING_ELEMENT
                ));
            }
        }
    }
}
