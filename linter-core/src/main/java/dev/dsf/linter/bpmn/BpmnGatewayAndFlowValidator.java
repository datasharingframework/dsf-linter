package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.FloatingElementType;
import dev.dsf.linter.output.FlowElementType;
import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.ValidationType;
import dev.dsf.linter.output.item.BpmnElementValidationItem;
import dev.dsf.linter.output.item.BpmnElementValidationItemSuccess;
import dev.dsf.linter.output.item.BpmnFloatingElementValidationItem;
import dev.dsf.linter.output.item.BpmnFlowElementValidationItem;
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.bpmn.BpmnElementValidator.checkExecutionListenerClasses;
import static dev.dsf.linter.util.validation.ValidationUtils.isEmpty;

/**
 * The {@code BpmnGatewayAndFlowValidator} class handles validation logic for BPMN gateway and flow elements,
 * including {@link ExclusiveGateway}, {@link InclusiveGateway}, {@link SequenceFlow}, and {@link EventBasedGateway}.
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
            if (isEmpty(gateway.getName())) {
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
     * Validates an {@link InclusiveGateway}.
     * <p>
     * The validation checks if the gateway's name is empty when there are multiple outgoing flows.
     * <ul>
     *   <li>If the gateway has multiple outgoing sequence flows and its name is empty, a warning is added.</li>
     *   <li>If the gateway has multiple outgoing flows and a non-empty name, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param gateway   the {@link InclusiveGateway} to be validated
     * @param issues    the list of {@link BpmnElementValidationItem} to which any validation issues will be added
     * @param bpmnFile  the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the gateway
     */
    public void validateInclusiveGateway(
            InclusiveGateway gateway,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = gateway.getId();

        // Check only if there are multiple outgoing flows.
        if (gateway.getOutgoing() != null && gateway.getOutgoing().size() > 1) {
            if (isEmpty(gateway.getName())) {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Inclusive Gateway has multiple outgoing flows but name is empty.",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.WARN,
                        FloatingElementType.INCLUSIVE_GATEWAY_HAS_MULTIPLE_OUTGOING_FLOWS_BUT_NAME_IS_EMPTY
                ));
            } else {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "Inclusive Gateway has multiple outgoing flows and a non-empty name: '" + gateway.getName() + "'"
                ));
            }
        }
    }

    /**
     * Validates a {@link SequenceFlow} based on its source element's outgoing flows.
     * <p>
     * For a source with multiple outgoing flows, the method performs the following checks:
     * <ul>
     *   <li>If the sequence flow's name is empty, a warning is logged.</li>
     *   <li>If the sequence flow's name is non-empty, a success item is logged.</li>
     *   <li>For {@link ExclusiveGateway} and {@link InclusiveGateway} sources:
     *     <ul>
     *       <li>If the flow is a default flow and has a condition expression, a warning is logged.</li>
     *       <li>If the flow is a default flow without a condition, a success item is logged.</li>
     *       <li>If the flow is not a default flow and lacks a condition expression, a warning is logged.</li>
     *       <li>If the flow is not a default flow and has a condition expression, a success item is logged.</li>
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
        FlowNode sourceNode = flow.getSource();

        // Check if source node exists
        if (sourceNode == null) {
            issues.add(new BpmnFlowElementValidationItem(
                    elementId,
                    bpmnFile,
                    processId,
                    "Sequence flow has no source node.",
                    ValidationType.BPMN_FLOW_ELEMENT,
                    ValidationSeverity.ERROR,
                    FlowElementType.SEQUENCE_FLOW_HAS_NO_SOURCE_NODE
            ));
            return;
        }

        // Check only if there are multiple outgoing flows
        if (sourceNode.getOutgoing() != null && sourceNode.getOutgoing().size() > 1) {
            // Check the sequence flow name
            validateSequenceFlowName(flow, elementId, issues, bpmnFile, processId);

            // Check conditions for gateways
            if (sourceNode instanceof ExclusiveGateway) {
                validateGatewayFlowCondition(flow, (ExclusiveGateway) sourceNode, elementId, issues, bpmnFile, processId, "ExclusiveGateway");
            } else if (sourceNode instanceof InclusiveGateway) {
                validateGatewayFlowCondition(flow, (InclusiveGateway) sourceNode, elementId, issues, bpmnFile, processId, "InclusiveGateway");
            }
        }
    }

    /**
     * Validates the name of a sequence flow originating from a source with multiple outgoing flows.
     */
    private void validateSequenceFlowName(
            SequenceFlow flow,
            String elementId,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        if (isEmpty(flow.getName())) {
            issues.add(new BpmnFlowElementValidationItem(
                    elementId,
                    bpmnFile,
                    processId,
                    "Sequence flow originates from a source with multiple outgoing flows and name is empty.",
                    ValidationType.BPMN_FLOW_ELEMENT,
                    ValidationSeverity.WARN,
                    FlowElementType.SEQUENCE_FLOW_ORIGINATES_FROM_A_SOURCE_WITH_MULTIPLE_OUTGOING_FLOWS_AND_NAME_IS_EMPTY
            ));
        } else {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "Sequence flow originates from a source with multiple outgoing flows and has a valid name: '" + flow.getName() + "'"
            ));
        }
    }

    /**
     * Validates the condition expression for sequence flows from gateways.
     * Handles both ExclusiveGateway and InclusiveGateway with the same logic.
     */
    private void validateGatewayFlowCondition(
            SequenceFlow flow,
            Gateway gateway,
            String elementId,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId,
            String gatewayType)
    {
        boolean isDefaultFlow = false;

        // Check if this is the default flow
        if (gateway instanceof ExclusiveGateway) {
            isDefaultFlow = flow.equals(((ExclusiveGateway) gateway).getDefault());
        } else if (gateway instanceof InclusiveGateway) {
            isDefaultFlow = flow.equals(((InclusiveGateway) gateway).getDefault());
        }

        if (isDefaultFlow) {
            // Default flow should NOT have a condition
            if (flow.getConditionExpression() != null) {
                issues.add(new BpmnFlowElementValidationItem(
                        elementId,
                        bpmnFile,
                        processId,
                        "Default sequence flow from " + gatewayType + " should not have a condition expression.",
                        ValidationType.BPMN_FLOW_ELEMENT,
                        ValidationSeverity.WARN,
                        FlowElementType.DEFAULT_SEQUENCE_FLOW_HAS_CONDITION_EXPRESSION
                ));
            } else {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "Default sequence flow from " + gatewayType + " correctly has no condition expression."
                ));
            }
        } else {
            // Non-default flow should have a condition
            if (flow.getConditionExpression() == null) {
                issues.add(new BpmnFlowElementValidationItem(
                        elementId,
                        bpmnFile,
                        processId,
                        "Non-default sequence flow from " + gatewayType + " is missing a condition expression.",
                        ValidationType.BPMN_FLOW_ELEMENT,
                        ValidationSeverity.WARN,
                        FlowElementType.NON_DEFAULT_SEQUENCE_FLOW_IS_MISSING_A_CONDITION_EXPRESSION
                ));
            } else {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "Non-default sequence flow from " + gatewayType + " has a valid condition expression."
                ));
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
        checkExecutionListenerClasses(gateway, gateway.getId(), issues, bpmnFile, processId, projectRoot);
    }
}