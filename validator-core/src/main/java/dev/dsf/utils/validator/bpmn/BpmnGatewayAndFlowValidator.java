package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.FloatingElementType;
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
     * Validates a {@link SequenceFlow} based on its source element and name/condition constraints.
     * <p>
     * This validation specifically checks whether the source {@link FlowNode} has multiple outgoing
     * sequence flows. If so, several conditions apply:
     * <ul>
     *   <li><strong>Sequence Flow Name Check:</strong>
     *       <ul>
     *         <li>If the name is empty and the source node is determined to be floating (see {@link #isFloatingNode(FlowNode)}),
     *             a {@link BpmnFloatingElementValidationItem} is added (warning level).</li>
     *         <li>If the name is empty but the source node is not floating, a
     *             {@link BpmnSequenceFlowOriginatesFromSourceWithMultipleOutgoingAndNameIsEmpty} item is added (warning level).</li>
     *       </ul>
     *   </li>
     *   <li><strong>Exclusive Gateway Condition Check:</strong>
     *       <ul>
     *         <li>If the source is an {@link ExclusiveGateway}, and this sequence flow is not the default flow,
     *             then the condition expression must be specified. If it is missing, a
     *             {@link BpmnFloatingElementValidationItem} is added (error level).</li>
     *       </ul>
     *   </li>
     *   <li><strong>Success Case:</strong>
     *       <ul>
     *         <li>If both the name check and (if applicable) the condition expression check pass, a success
     *             validation item is recorded.</li>
     *       </ul>
     *   </li>
     * </ul>
     * </p>
     *
     * @param flow      the {@link SequenceFlow} to be validated
     * @param issues    a list of {@link BpmnElementValidationItem} where any discovered validation issues (warnings, errors, or successes) are added
     * @param bpmnFile  the BPMN file being validated
     * @param processId the process definition ID or key associated with this flow
     *
     * <h2>Example Usage</h2>
     * <pre>{@code
     * List<BpmnElementValidationItem> issues = new ArrayList<>();
     * SequenceFlow flow = ...;  // retrieve or create a SequenceFlow
     * File bpmnFile = ...;      // reference to the BPMN file
     * String processId = "myProcess";
     *
     * validateSequenceFlow(flow, issues, bpmnFile, processId);
     *
     * // 'issues' will be populated with any warnings, errors, or success items found.
     * }</pre>
     *
     * <h3>References and Further Reading</h3>
     * <ul>
     *   <li>
     *     <a href="https://www.omg.org/spec/BPMN/2.0/PDF">
     *       BPMN 2.0 Specification (Object Management Group)
     *     </a>
     *   </li>
     *   <li>
     *     <a href="https://docs.camunda.org/manual/latest/">
     *       Camunda BPM Documentation
     *     </a>
     *   </li>
     *   <li>
     *     <a href="https://flowable.com/open-source/docs/">
     *       Flowable Documentation
     *     </a>
     *   </li>
     * </ul>
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
            // Only apply these checks if the source node has more than one outgoing flow
            if (flowNode.getOutgoing() != null && flowNode.getOutgoing().size() > 1)
            {
                // 1) Check if the sequence flow name is valid (non-empty)
                boolean nameValid = !BpmnValidationUtils.isEmpty(flow.getName());
                if (!nameValid)
                {
                    boolean sourceIsFloating = isFloatingNode(flowNode);

                    if (sourceIsFloating)
                    {
                        // If the node is floating, we add a BpmnFloatingElementValidationItem
                        issues.add(new BpmnFloatingElementValidationItem(
                                elementId,
                                bpmnFile,
                                processId,
                                "Sequence flow originates from a FLOATING source with multiple outgoing flows and name is empty.",
                                ValidationType.BPMN_FLOATING_ELEMENT,
                                ValidationSeverity.WARN,
                                FloatingElementType.SEQUENCE_FLOW_ORIGINATES_FROM_A_SOURCE_WITH_MULTIPLE_OUTGOING_FLOWS_AND_NAME_IS_EMPTY
                        ));
                    }
                    else
                    {
                        // If the node is NOT floating, we add our specialized validation item
                        issues.add(new BpmnSequenceFlowOriginatesFromSourceWithMultipleOutgoingAndNameIsEmpty(
                                elementId,
                                bpmnFile,
                                processId
                        ));
                    }
                }

                // 2) If the source is an ExclusiveGateway (non-default flow),
                //    validate condition expression
                boolean conditionValid = true;
                if (flowNode instanceof ExclusiveGateway gateway)
                {
                    // Make sure the flow is not the default, and if so, check the condition expression
                    if (!flow.equals(gateway.getDefault()))
                    {
                        if (flow.getConditionExpression() == null)
                        {
                            conditionValid = false;
                            issues.add(new BpmnFloatingElementValidationItem(
                                    elementId,
                                    bpmnFile,
                                    processId,
                                    "Non-default sequence flow from an ExclusiveGateway is missing a condition expression.",
                                    ValidationType.BPMN_SEQUENCE_FLOW_AMBIGUOUS,
                                    ValidationSeverity.ERROR,
                                    FloatingElementType.NON_DEFAULT_SEQUENCE_FLOW_FROM_AN_EXCLUSIVE_GATEWAY_IS_MISSING_A_CONDITION_EXPRESSION
                            ));
                        }
                    }
                }

                // 3) If both validations pass, record a success
                if (nameValid && conditionValid)
                {
                    String successMsg = "Sequence flow from a source with multiple outgoing flows is valid: name is not empty";
                    if (flowNode instanceof ExclusiveGateway)
                    {
                        successMsg += " and condition expression is provided.";
                    }
                    else
                    {
                        successMsg += ".";
                    }
                    issues.add(new BpmnElementValidationItemSuccess(
                            elementId,
                            bpmnFile,
                            processId,
                            successMsg
                    ));
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

    /**
     * Determines whether a BPMN element of type {@code FlowNode} should be considered “floating,” meaning it is isolated.
     * <p>
     * In this implementation, a flow node is deemed “floating” if:
     * <ul>
     *   <li>The {@code node} reference itself is not {@code null},</li>
     *   <li>No incoming flows are defined ({@code getIncoming()} returns {@code null} or an empty list), and</li>
     *   <li>No outgoing flows are defined ({@code getOutgoing()} returns {@code null} or an empty list).</li>
     * </ul>
     * </p>
     *
     * @param node The {@code FlowNode} object to check (can be {@code null}).
     * @return {@code true} if the node is considered “floating,” otherwise {@code false}.
     *
     * <h2>Example Usage</h2>
     * <pre>{@code
     * FlowNode node = ...;
     * boolean isNodeFloating = isFloatingNode(node);
     * if (isNodeFloating) {
     *     // The node is isolated in the BPMN diagram
     * } else {
     *     // The node has at least one incoming or outgoing flow
     * }
     * }</pre>
     *
     * <h3>Implementation Details</h3>
     * <p>
     * The check is performed through these steps:
     * <ul>
     *   <li>A {@code null} check on the {@code node} to avoid {@link NullPointerException}.</li>
     *   <li>Examining {@code node.getIncoming()} and {@code node.getOutgoing()} for {@code null} or empty lists.</li>
     *   <li>Returning {@code true} only if both the incoming and outgoing lists are either {@code null} or empty.</li>
     * </ul>
     * </p>
     *
     * <h3>See Also</h3>
     * <ul>
     *   <li>{@link org.camunda.bpm.model.bpmn.instance.FlowNode}</li>
     *   <li>{@link org.camunda.bpm.model.bpmn.instance.SequenceFlow}</li>
     * </ul>
     *
     * <h3>Further Reading and Sources</h3>
     * <ul>
     *   <li>
     *     <a href="https://www.omg.org/spec/BPMN/2.0/PDF">
     *       BPMN 2.0 Specification (Object Management Group)
     *     </a>
     *   </li>
     *   <li>
     *     <a href="https://docs.camunda.org/manual/latest/">
     *       Camunda BPM Documentation
     *     </a>
     *   </li>
     *   <li>
     *     <a href="https://flowable.com/open-source/docs/">
     *       Flowable Documentation
     *     </a>
     *   </li>
     * </ul>
     */
    private boolean isFloatingNode(FlowNode node) {
        if (node == null) {
            return false;
        }
        boolean noIncoming = (node.getIncoming() == null || node.getIncoming().isEmpty());
        boolean noOutgoing = (node.getOutgoing() == null || node.getOutgoing().isEmpty());
        return noIncoming && noOutgoing;
    }

}
