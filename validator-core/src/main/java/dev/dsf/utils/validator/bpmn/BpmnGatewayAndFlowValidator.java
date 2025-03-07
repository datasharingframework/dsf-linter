package dev.dsf.utils.validator.bpmn;

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
     * If the gateway has multiple outgoing sequence flows and its name is empty, a warning is added to
     * the list of validation issues.
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

        if (BpmnValidationUtils.isEmpty(gateway.getName()))
        {
            if (gateway.getOutgoing() != null && gateway.getOutgoing().size() > 1)
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Exclusive Gateway has multiple outgoing flows but name is empty.",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.WARN
                ));
            }
        }
    }

    /**
     * Validates a {@link SequenceFlow} based on its source element's outgoing flows.
     * <p>
     * The validation applies only when the source element has more than one outgoing sequence flow.
     * The checks include:
     * </p>
     * <ul>
     *   <li>A warning that the sequence flow originates from a source with multiple outgoing flows.</li>
     *   <li>A warning if the sequence flow name is empty.</li>
     *   <li>An error if the condition expression is missing and the sequence flow is not the default flow
     *       (for {@link ExclusiveGateway} sources).</li>
     * </ul>
     *
     * @param flow      the {@link SequenceFlow} to be validated
     * @param issues    the list of {@link BpmnElementValidationItem} to which any validation issues will be added
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
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Sequence flow originates from a source with multiple outgoing flows.",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.WARN
                ));

                if (BpmnValidationUtils.isEmpty(flow.getName()))
                {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Sequence flow name is empty.",
                            ValidationType.BPMN_FLOATING_ELEMENT,
                            ValidationSeverity.WARN
                    ));
                }

                if (flow.getConditionExpression() == null)
                {
                    // For ExclusiveGateway sources, only non-default flows must have a condition
                    if (flowNode instanceof ExclusiveGateway gateway)
                    {
                        if (!flow.equals(gateway.getDefault()))
                        {
                            issues.add(new BpmnFloatingElementValidationItem(
                                    elementId, bpmnFile, processId,
                                    "Non-default sequence flow from an ExclusiveGateway is missing a condition expression.",
                                    ValidationType.BPMN_SEQUENCE_FLOW_AMBIGUOUS,
                                    ValidationSeverity.ERROR
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
