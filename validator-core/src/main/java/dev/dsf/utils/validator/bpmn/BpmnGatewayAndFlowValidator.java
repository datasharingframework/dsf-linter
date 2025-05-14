package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.FloatingElementType;
import dev.dsf.utils.validator.FlowElementType;
import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.BpmnValidationUtils;
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;

import java.io.File;
import java.util.List;

/**
 * The {@code BpmnGatewayAndFlowValidator} class handles validation logic for BPMN gateway and flow elements,
 * including {@link ExclusiveGateway}, {@link SequenceFlow}, and {@link EventBasedGateway}.
 * <p>
 * This class performs various checks on BPMN elements such as ensuring that gateways have appropriate naming
 * when multiple outgoing flows exist, that sequence flows from nodes with multiple outgoing flows have proper
 * condition expressions and non-empty names, and that event-based gateways have valid execution listener classes
 * configured.
 * </p>
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
public class BpmnGatewayAndFlowValidator
{
    private final File projectRoot;

    /**
     * Constructs a new {@code BpmnGatewayAndFlowValidator} with the specified project root.
     * <p>
     * The project root is used to locate FHIR resources when validating BPMN elements.
     * </p>
     *
     * @param projectRoot the root directory of the project containing FHIR and BPMN resources
     */
    public BpmnGatewayAndFlowValidator(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    /**
     * Validates an {@link ExclusiveGateway}.
     * <p>
     * The validation checks if the gateway's name is empty when there are multiple outgoing flows.
     * <ul>
     *   <li>If the gateway has multiple outgoing sequence flows and its name is empty, a warning is added.</li>
     *   <li>If the gateway has multiple outgoing flows and a non-empty name, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param gateway   the {@link ExclusiveGateway} to be validated
     * @param issues    the list of {@link BpmnElementValidationItem} to which any validation issues will be added
     * @param bpmnFile  the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the gateway
     */
    public void validateExclusiveGateway(
            ExclusiveGateway gateway,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = gateway.getId();

        // Check only if there are multiple outgoing flows.
        if (gateway.getOutgoing() != null && gateway.getOutgoing().size() > 1) {
            if (BpmnValidationUtils.isEmpty(gateway.getName())) {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Exclusive Gateway has multiple outgoing flows but name is empty.",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.WARN,
                        FloatingElementType.EXCLUSIVE_GATEWAY_HAS_MULTIPLE_OUTGOING_FLOWS_BUT_NAME_IS_EMPTY
                ));
            } else {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "Exclusive Gateway has multiple outgoing flows and a non-empty name: '" + gateway.getName() + "'"
                ));
            }
        }
    }

    /**
     * Validates a {@link SequenceFlow} based on its source element's outgoing flows.
     * <p>
     * For a source with multiple outgoing flows, the method performs individual checks:
     * <ul>
     *   <li>If the sequence flow's name is empty, a success item is logged with a corresponding message.</li>
     *   <li>If the sequence flow's name is non-empty, a success item is logged indicating a valid name.</li>
     *   <li>For an {@link ExclusiveGateway} source, if the sequence flow is not the default flow, then:
     *     <ul>
     *       <li>If the condition expression is missing, a success item is logged with the message
     *           "Non-default sequence flow from an ExclusiveGateway is missing a condition expression."</li>
     *       <li>If the condition expression is present, a success item is logged indicating a valid condition expression.</li>
     *     </ul>
     *   </li>
     * </ul>
     * </p>
     *
     * @param flow      the {@link SequenceFlow} to be validated
     * @param issues    the list of {@link BpmnElementValidationItem} where validation results will be added
     * @param bpmnFile  the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the flow
     */
    public void validateSequenceFlow(
            SequenceFlow flow,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = flow.getId();
        if (flow.getSource() instanceof FlowNode flowNode)
        {
            if (flowNode.getOutgoing() != null && flowNode.getOutgoing().size() > 1)
            {
                // Check the sequence flow name.
                if (BpmnValidationUtils.isEmpty(flow.getName()))
                {
                    issues.add(new BpmnFlowElementValidationItem(
                            elementId,
                            bpmnFile,
                            processId,
                            "Sequence flow originates from a source with multiple outgoing flows and name is empty.",
                            ValidationType.BPMN_FLOW_ELEMENT,
                            ValidationSeverity.WARN,
                            FlowElementType.SEQUENCE_FLOW_ORIGINATES_FROM_A_SOURCE_WITH_MULTIPLE_OUTGOING_FLOWS_AND_NAME_IS_EMPTY
                            //todo the name of type should be clearer
                    ));
                }
                else
                {
                    issues.add(new BpmnElementValidationItemSuccess(
                            elementId,
                            bpmnFile,
                            processId,
                            "Sequence flow originates from a source with multiple outgoing flows and has a valid name."
                    ));
                }

                // Check the condition expression for flows from an ExclusiveGateway (if the flow is not the default).
                if (flowNode instanceof ExclusiveGateway gateway)
                {
                    if (!flow.equals(gateway.getDefault()))
                    {
                        if (flow.getConditionExpression() == null)
                        {
                            issues.add(new BpmnFlowElementValidationItem(
                                    elementId,
                                    bpmnFile,
                                    processId,
                                    "Non-default sequence flow from an ExclusiveGateway is missing a condition expression.",
                                    ValidationType.BPMN_FLOW_ELEMENT,
                                    ValidationSeverity.WARN,
                                    FlowElementType.NON_DEFAULT_SEQUENCE_FLOW_FROM_AN_EXCLUSIVE_GATEWAY_IS_MISSING_A_CONDITION_EXPRESSION
                            ));
                        }
                        else
                        {
                            issues.add(new BpmnElementValidationItemSuccess(
                                    elementId,
                                    bpmnFile,
                                    processId,
                                    "Non-default sequence flow from an ExclusiveGateway has a valid condition expression."
                            ));
                        }
                    }
                }
            }
        }
    }


    /**
     * Validates an {@link EventBasedGateway} by checking for extension elements (execution listeners)
     * that specify a Java class, and verifying that each referenced class can be found on the classpath.
     * <p>
     * This validation helps ensure that any execution listener classes configured via extension elements
     * are available at runtime.
     * </p>
     *
     * @param gateway   the {@link EventBasedGateway} to be validated
     * @param issues    the list of {@link BpmnElementValidationItem} to which any validation issues will be added
     * @param bpmnFile  the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the gateway
     */
    public void validateEventBasedGateway(
            EventBasedGateway gateway,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        BpmnValidationUtils.checkExecutionListenerClasses(gateway, gateway.getId(), issues, bpmnFile, processId, projectRoot);
    }
}
