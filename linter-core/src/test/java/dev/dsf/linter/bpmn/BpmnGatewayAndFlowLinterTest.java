package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.item.BpmnElementLintItem;
import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.EventBasedGateway;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * <p>
 * Test class for {@link BpmnGatewayAndFlowLinter}. It verifies linting logic
 * for ExclusiveGateway, SequenceFlow, and EventBasedGateway elements.
 * </p>
 */
class BpmnGatewayAndFlowLinterTest {

    private BpmnGatewayAndFlowLinter linter;
    private File mockBpmnFile;

    @BeforeEach
    public void setUp() {
        linter = new BpmnGatewayAndFlowLinter(new File("mockProjectRoot"));
        mockBpmnFile = new File("testProcess.bpmn");
    }

    /**
     * Tests {@link BpmnGatewayAndFlowLinter#lintExclusiveGateway(ExclusiveGateway, List, File, String)}
     * when the gateway has multiple outgoing flows but no name.
     */
    @Test
    public void testLintExclusiveGateway_EmptyName_MultipleOutgoing() {
        // Arrange
        ExclusiveGateway gateway = mock(ExclusiveGateway.class);
        when(gateway.getId()).thenReturn("exclusiveGwId");
        when(gateway.getName()).thenReturn("");

        SequenceFlow flow1 = mock(SequenceFlow.class);
        SequenceFlow flow2 = mock(SequenceFlow.class);
        Collection<SequenceFlow> outgoingFlows = Arrays.asList(flow1, flow2);

        when(gateway.getOutgoing()).thenReturn(outgoingFlows);

        List<BpmnElementLintItem> issues = new ArrayList<>();

        // Act
        linter.lintExclusiveGateway(gateway, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty(), "Expected a warning due to empty name on gateway with multiple outgoing flows.");
    }

    /**
     * Tests {@link BpmnGatewayAndFlowLinter#lintSequenceFlow(SequenceFlow, List, File, String)}
     * where the source FlowNode has multiple outgoing flows but the tested sequence flow has no condition.
     */
    @Test
    public void testLintSequenceFlow_NoCondition_MultipleOutgoing() {
        // Arrange
        SequenceFlow sequenceFlow = mock(SequenceFlow.class);
        when(sequenceFlow.getId()).thenReturn("sequenceFlowId");
        when(sequenceFlow.getName()).thenReturn("");  // name is empty

        FlowNode flowNode = mock(ExclusiveGateway.class);
        when(flowNode.getOutgoing()).thenReturn(Arrays.asList(sequenceFlow, mock(SequenceFlow.class)));

        // The sequence flow references the gateway as source
        when(sequenceFlow.getSource()).thenReturn(flowNode);
        when(sequenceFlow.getConditionExpression()).thenReturn(null);

        List<BpmnElementLintItem> issues = new ArrayList<>();

        // Act
        linter.lintSequenceFlow(sequenceFlow, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertTrue(issues.stream().anyMatch(item -> item.getDescription().contains("missing a condition expression")),
                "We expect an error due to no condition expression on a non-default sequence flow from an ExclusiveGateway.");
    }

    /**
     * Tests {@link BpmnGatewayAndFlowLinter#lintEventBasedGateway(EventBasedGateway, List, File, String)}
     * with minimal mocking. Checks that no exception is thrown and issues might appear if we are
     * checking for nonexistent classes.
     */
    @Test
    public void testLintEventBasedGateway_Basic() {
        // Arrange
        EventBasedGateway gateway = mock(EventBasedGateway.class);
        when(gateway.getId()).thenReturn("eventBasedGwId");

        List<BpmnElementLintItem> issues = new ArrayList<>();

        // Act
        linter.lintEventBasedGateway(gateway, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertTrue(true, "Method completed without exception.");
    }
}
