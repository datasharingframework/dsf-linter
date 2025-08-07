package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.item.BpmnElementValidationItem;
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
 * Test class for {@link BpmnGatewayAndFlowValidator}. It verifies validation logic
 * for ExclusiveGateway, SequenceFlow, and EventBasedGateway elements.
 * </p>
 */
class BpmnGatewayAndFlowValidatorTest {

    private BpmnGatewayAndFlowValidator validator;
    private File mockBpmnFile;

    @BeforeEach
    public void setUp() {
        validator = new BpmnGatewayAndFlowValidator(new File("mockProjectRoot"));
        mockBpmnFile = new File("testProcess.bpmn");
    }

    /**
     * Tests {@link BpmnGatewayAndFlowValidator#validateExclusiveGateway(ExclusiveGateway, List, File, String)}
     * when the gateway has multiple outgoing flows but no name.
     */
    @Test
    public void testValidateExclusiveGateway_EmptyName_MultipleOutgoing() {
        // Arrange
        ExclusiveGateway gateway = mock(ExclusiveGateway.class);
        when(gateway.getId()).thenReturn("exclusiveGwId");
        when(gateway.getName()).thenReturn("");

        SequenceFlow flow1 = mock(SequenceFlow.class);
        SequenceFlow flow2 = mock(SequenceFlow.class);
        Collection<SequenceFlow> outgoingFlows = Arrays.asList(flow1, flow2);

        when(gateway.getOutgoing()).thenReturn(outgoingFlows);

        List<BpmnElementValidationItem> issues = new ArrayList<>();

        // Act
        validator.validateExclusiveGateway(gateway, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty(), "Expected a warning due to empty name on gateway with multiple outgoing flows.");
    }

    /**
     * Tests {@link BpmnGatewayAndFlowValidator#validateSequenceFlow(SequenceFlow, List, File, String)}
     * where the source FlowNode has multiple outgoing flows but the tested sequence flow has no condition.
     */
    @Test
    public void testValidateSequenceFlow_NoCondition_MultipleOutgoing() {
        // Arrange
        SequenceFlow sequenceFlow = mock(SequenceFlow.class);
        when(sequenceFlow.getId()).thenReturn("sequenceFlowId");
        when(sequenceFlow.getName()).thenReturn("");  // name is empty

        FlowNode flowNode = mock(ExclusiveGateway.class);
        when(flowNode.getOutgoing()).thenReturn(Arrays.asList(sequenceFlow, mock(SequenceFlow.class)));

        // The sequence flow references the gateway as source
        when(sequenceFlow.getSource()).thenReturn(flowNode);
        when(sequenceFlow.getConditionExpression()).thenReturn(null);

        List<BpmnElementValidationItem> issues = new ArrayList<>();

        // Act
        validator.validateSequenceFlow(sequenceFlow, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertTrue(issues.stream().anyMatch(item -> item.getDescription().contains("missing a condition expression")),
                "We expect an error due to no condition expression on a non-default sequence flow from an ExclusiveGateway.");
    }

    /**
     * Tests {@link BpmnGatewayAndFlowValidator#validateEventBasedGateway(EventBasedGateway, List, File, String)}
     * with minimal mocking. Checks that no exception is thrown and issues might appear if we are
     * checking for nonexistent classes.
     */
    @Test
    public void testValidateEventBasedGateway_Basic() {
        // Arrange
        EventBasedGateway gateway = mock(EventBasedGateway.class);
        when(gateway.getId()).thenReturn("eventBasedGwId");

        List<BpmnElementValidationItem> issues = new ArrayList<>();

        // Act
        validator.validateEventBasedGateway(gateway, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertTrue(true, "Method completed without exception.");
    }
}
