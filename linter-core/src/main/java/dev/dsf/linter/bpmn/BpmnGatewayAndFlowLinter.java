package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.FlowElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.output.item.BpmnElementLintItem;
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkExecutionListenerClasses;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Specialized linter class for validating BPMN gateway and flow elements against business logic and best practices.
 *
 * <p>
 * The {@code BpmnGatewayAndFlowLinter} provides comprehensive validation for BPMN 2.0 gateway types and sequence flows
 * used in Camunda workflows. It ensures that gateways and flows are properly configured with appropriate names,
 * condition expressions, and execution listeners to maintain process clarity and correctness.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * This class is a specialized sub-linter that is invoked by the {@link BpmnModelLinter} during the validation
 * of a complete BPMN model. It focuses exclusively on gateway and flow-related validations, delegating execution
 * listener class validation to shared utility methods from {@link BpmnElementLinter}.
 * </p>
 *
 * <h2>Validation Categories</h2>
 * <p>
 * The linter performs the following categories of checks:
 * </p>
 *
 * <h3>Gateway Validation</h3>
 * <ul>
 *   <li><strong>Exclusive Gateways</strong>: Validates that exclusive gateways with multiple outgoing flows
 *       have non-empty names to improve process readability. Also validates execution listener class implementations.</li>
 *   <li><strong>Inclusive Gateways</strong>: Validates that inclusive gateways with multiple outgoing flows
 *       have non-empty names. Also validates execution listener class implementations.</li>
 *   <li><strong>Event-Based Gateways</strong>: Validates execution listener class implementations for
 *       event-based gateways.</li>
 * </ul>
 *
 * <h3>Sequence Flow Validation</h3>
 * <ul>
 *   <li><strong>Source Node Validation</strong>: Ensures that sequence flows have a valid source node.
 *       Issues an error if the source node is missing.</li>
 *   <li><strong>Flow Naming</strong>: Validates that sequence flows originating from sources with multiple
 *       outgoing flows have non-empty names. This improves process documentation and debugging capabilities.</li>
 *   <li><strong>Condition Expression Validation</strong>: For flows originating from Exclusive or Inclusive Gateways:
 *       <ul>
 *         <li><strong>Default Flows</strong>: Validates that default sequence flows do not have condition expressions,
 *             as default flows are taken when no other conditions are met.</li>
 *         <li><strong>Non-Default Flows</strong>: Validates that non-default sequence flows have condition expressions
 *             to clearly define when each path should be taken.</li>
 *       </ul>
 *   </li>
 *   <li><strong>Execution Listener Validation</strong>: Validates that execution listener classes exist and
 *       implement the required Camunda interfaces.</li>
 * </ul>
 *
 * <h2>Validation Logic</h2>
 * <p>
 * The linter applies different validation rules based on the context:
 * </p>
 * <ul>
 *   <li>Gateway name validation is only performed when a gateway has multiple outgoing flows, as single-path
 *       gateways do not require descriptive names for clarity.</li>
 *   <li>Sequence flow name validation is only performed when the source node has multiple outgoing flows,
 *       ensuring that decision points are properly documented.</li>
 *   <li>Condition expression validation distinguishes between default and non-default flows, enforcing
 *       that default flows remain unconditional while non-default flows must have conditions.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * File projectRoot = new File("/path/to/project");
 * BpmnGatewayAndFlowLinter linter = new BpmnGatewayAndFlowLinter(projectRoot);
 *
 * List<BpmnElementLintItem> issues = new ArrayList<>();
 * File bpmnFile = new File("process.bpmn");
 * String processId = "myProcess";
 *
 * ExclusiveGateway gateway = // ... obtain gateway from model
 * linter.lintExclusiveGateway(gateway, issues, bpmnFile, processId);
 *
 * SequenceFlow flow = // ... obtain flow from model
 * linter.lintSequenceFlow(flow, issues, bpmnFile, processId);
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
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/bpmn-model/gateways/">Camunda Gateways</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/bpmn-model/sequence-flows/">Camunda Sequence Flows</a></li>
 * </ul>
 *
 * @see BpmnModelLinter
 * @see BpmnElementLinter
 * @see BpmnElementLintItem
 * @since 1.0
 */
public record BpmnGatewayAndFlowLinter(File projectRoot) {

    /**
     * Lints an {@link ExclusiveGateway}.
     */
    public void lintExclusiveGateway(
            ExclusiveGateway gateway,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = gateway.getId();

        // Check only if there are multiple outgoing flows.
        if (gateway.getOutgoing() != null && gateway.getOutgoing().size() > 1) {
            if (isEmpty(gateway.getName())) {
                issues.add(BpmnElementLintItem.of(
                        LinterSeverity.ERROR,
                        LintingType.BPMN_EXCLUSIVE_GATEWAY_HAS_MULTIPLE_OUTGOING_FLOWS_BUT_NAME_IS_EMPTY,
                        elementId, bpmnFile, processId));
            } else {
                issues.add(BpmnElementLintItem.success(
                        elementId,
                        bpmnFile,
                        processId,
                        "Exclusive Gateway has multiple outgoing flows and a non-empty name: '" + gateway.getName() + "'"
                ));
            }
        }

        checkExecutionListenerClasses(gateway, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * lints an {@link InclusiveGateway}.
     * <p>
     * The linter checks if the gateway's name is empty when there are multiple outgoing flows.
     * <ul>
     *   <li>If the gateway has multiple outgoing sequence flows and its name is empty, a warning is added.</li>
     *   <li>If the gateway has multiple outgoing flows and a non-empty name, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param gateway   the {@link InclusiveGateway} to be linted
     * @param issues    the list of {@link BpmnElementLintItem} to which any linter issues will be added
     * @param bpmnFile  the BPMN file under linter
     * @param processId the identifier of the BPMN process containing the gateway
     */
    public void lintInclusiveGateway(
            InclusiveGateway gateway,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = gateway.getId();

        // Check only if there are multiple outgoing flows.
        if (gateway.getOutgoing() != null && gateway.getOutgoing().size() > 1) {
            if (isEmpty(gateway.getName())) {
                issues.add(BpmnElementLintItem.of(
                        LinterSeverity.ERROR,
                        LintingType.BPMN_INCLUSIVE_GATEWAY_HAS_MULTIPLE_OUTGOING_FLOWS_BUT_NAME_IS_EMPTY,
                        elementId, bpmnFile, processId));
            } else {
                issues.add(BpmnElementLintItem.success(
                        elementId,
                        bpmnFile,
                        processId,
                        "Inclusive Gateway has multiple outgoing flows and a non-empty name: '" + gateway.getName() + "'"
                ));
            }
        }

        checkExecutionListenerClasses(gateway, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * lints a {@link SequenceFlow} based on its source element's outgoing flows.
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
     * @param flow      the {@link SequenceFlow} to be linted
     * @param issues    the list of {@link BpmnElementLintItem} where linter results will be added
     * @param bpmnFile  the BPMN file under linter
     * @param processId the identifier of the BPMN process containing the flow
     */
    public void lintSequenceFlow(
            SequenceFlow flow,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        String elementId = flow.getId();
        FlowNode sourceNode = flow.getSource();

        // Check if source node exists
        if (sourceNode == null) {
            issues.add(new BpmnFlowElementLintItem(
                    LinterSeverity.ERROR,
                    LintingType.BPMN_FLOW_ELEMENT,
                    elementId,
                    bpmnFile,
                    processId,
                    "Sequence flow has no source node.",
                    FlowElementType.SEQUENCE_FLOW_HAS_NO_SOURCE_NODE
            ));
            return;
        }

        // Check only if there are multiple outgoing flows
        if (sourceNode.getOutgoing() != null && sourceNode.getOutgoing().size() > 1) {
            // Check the sequence flow name
            lintSequenceFlowName(flow, elementId, issues, bpmnFile, processId);

            // Check conditions for gateways
            if (sourceNode instanceof ExclusiveGateway) {
                lintGatewayFlowCondition(flow, (ExclusiveGateway) sourceNode, elementId, issues, bpmnFile, processId, "ExclusiveGateway");
            } else if (sourceNode instanceof InclusiveGateway) {
                lintGatewayFlowCondition(flow, (InclusiveGateway) sourceNode, elementId, issues, bpmnFile, processId, "InclusiveGateway");
            }
        }

        checkExecutionListenerClasses(flow, elementId, issues, bpmnFile, processId, projectRoot);
    }

    /**
     * lints the name of a sequence flow originating from a source with multiple outgoing flows.
     */
    private void lintSequenceFlowName(
            SequenceFlow flow,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        if (isEmpty(flow.getName())) {
            issues.add(new BpmnFlowElementLintItem(
                    LinterSeverity.WARN,
                    LintingType.BPMN_FLOW_ELEMENT,
                    elementId,
                    bpmnFile,
                    processId,
                    "Sequence flow originates from a source with multiple outgoing flows and name is empty.",
                    FlowElementType.SEQUENCE_FLOW_ORIGINATES_FROM_A_SOURCE_WITH_MULTIPLE_OUTGOING_FLOWS_AND_NAME_IS_EMPTY
            ));
        } else {
            issues.add(BpmnElementLintItem.success(
                    elementId,
                    bpmnFile,
                    processId,
                    "Sequence flow originates from a source with multiple outgoing flows and has a valid name: '" + flow.getName() + "'"
            ));
        }
    }

    /**
     * lints the condition expression for sequence flows from gateways.
     * Handles both ExclusiveGateway and InclusiveGateway with the same logic.
     */
    private void lintGatewayFlowCondition(
            SequenceFlow flow,
            Gateway gateway,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            String gatewayType) {
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
                issues.add(new BpmnFlowElementLintItem(
                        LinterSeverity.WARN,
                        LintingType.BPMN_FLOW_ELEMENT,
                        elementId,
                        bpmnFile,
                        processId,
                        "Default sequence flow from " + gatewayType + " should not have a condition expression.",
                        FlowElementType.DEFAULT_SEQUENCE_FLOW_HAS_CONDITION_EXPRESSION
                ));
            } else {
                issues.add(BpmnElementLintItem.success(
                        elementId,
                        bpmnFile,
                        processId,
                        "Default sequence flow from " + gatewayType + " correctly has no condition expression."
                ));
            }
        } else {
            // Non-default flow should have a condition
            if (flow.getConditionExpression() == null) {
                issues.add(new BpmnFlowElementLintItem(
                        LinterSeverity.WARN,
                        LintingType.BPMN_FLOW_ELEMENT,
                        elementId,
                        bpmnFile,
                        processId,
                        "Non-default sequence flow from " + gatewayType + " is missing a condition expression.",
                        FlowElementType.NON_DEFAULT_SEQUENCE_FLOW_IS_MISSING_A_CONDITION_EXPRESSION
                ));
            } else {
                issues.add(BpmnElementLintItem.success(
                        elementId,
                        bpmnFile,
                        processId,
                        "Non-default sequence flow from " + gatewayType + " has a valid condition expression."
                ));
            }
        }
    }

    /**
     * lints an {@link EventBasedGateway} by checking for extension elements (execution listeners)
     * that specify a Java class, and verifying that each referenced class can be found on the classpath.
     * <p>
     * This linter helps ensure that any execution listener classes configured via extension elements
     * are available at runtime.
     * </p>
     *
     * @param gateway   the {@link EventBasedGateway} to be linted
     * @param issues    the list of {@link BpmnElementLintItem} to which any linter issues will be added
     * @param bpmnFile  the BPMN file under linter
     * @param processId the identifier of the BPMN process containing the gateway
     */
    public void lintEventBasedGateway(
            EventBasedGateway gateway,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {
        checkExecutionListenerClasses(gateway, gateway.getId(), issues, bpmnFile, processId, projectRoot);
    }
}