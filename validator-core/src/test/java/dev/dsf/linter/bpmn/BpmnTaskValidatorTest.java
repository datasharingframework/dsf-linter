package dev.dsf.linter.bpmn;

import dev.dsf.linter.item.BpmnElementValidationItem;
import dev.dsf.linter.item.BpmnServiceTaskNameEmptyValidationItem;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.ReceiveTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * <p>
 * Test class for {@link BpmnTaskValidator}. Verifies the validation of ServiceTask, UserTask,
 * SendTask, and ReceiveTask elements.
 * </p>
 */
public class BpmnTaskValidatorTest {

    private BpmnTaskValidator validator;
    private File mockBpmnFile;

    @BeforeEach
    public void setUp() {
        validator = new BpmnTaskValidator(new File("mockProjectRoot"));
        mockBpmnFile = new File("testProcess.bpmn");
    }

    /**
     * Tests {@link BpmnTaskValidator#validateServiceTask(ServiceTask, List, File, String)}
     * when the task has an empty name.
     */
    @Test
    public void testValidateServiceTask_EmptyName() {
        // Arrange
        ServiceTask serviceTask = mock(ServiceTask.class);
        when(serviceTask.getId()).thenReturn("serviceTaskId");
        when(serviceTask.getName()).thenReturn("");
        when(serviceTask.getCamundaClass()).thenReturn("org.example.MyJavaDelegate");

        List<BpmnElementValidationItem> issues = new ArrayList<>();

        // Act
        validator.validateServiceTask(serviceTask, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i instanceof BpmnServiceTaskNameEmptyValidationItem),
                "We expect a BpmnServiceTaskNameEmptyValidationItem for empty service task name.");
    }

    /**
     * Tests {@link BpmnTaskValidator#validateUserTask(UserTask, List, File, String)}
     * with an empty formKey.
     */
    @Test
    public void testValidateUserTask_EmptyFormKey() {
        // Arrange
        UserTask userTask = mock(UserTask.class);
        when(userTask.getId()).thenReturn("userTaskId");
        when(userTask.getName()).thenReturn("Some User Task");
        when(userTask.getCamundaFormKey()).thenReturn("");

        List<BpmnElementValidationItem> issues = new ArrayList<>();

        // Act
        validator.validateUserTask(userTask, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty(), "Empty formKey should yield a validation issue.");
    }

    /**
     * Tests {@link BpmnTaskValidator#validateSendTask(SendTask, List, File, String)} with a non-empty
     * name but no implementation class.
     */
    @Test
    public void testValidateSendTask_NoImplementationClass() {
        // Arrange
        SendTask sendTask = mock(SendTask.class);
        when(sendTask.getId()).thenReturn("sendTaskId");
        when(sendTask.getName()).thenReturn("My Send Task");
        // No camundaClass set => return null or empty
        when(sendTask.getCamundaClass()).thenReturn(null);

        List<BpmnElementValidationItem> issues = new ArrayList<>();

        // Act
        validator.validateSendTask(sendTask, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty(), "Expected a validation issue if implementation class is missing.");
    }

    /**
     * Tests {@link BpmnTaskValidator#validateReceiveTask(ReceiveTask, List, File, String)}
     * when the message name is empty.
     */
    @Test
    public void testValidateReceiveTask_EmptyMessageName() {
        // Arrange
        ReceiveTask receiveTask = mock(ReceiveTask.class);
        when(receiveTask.getId()).thenReturn("receiveTaskId");
        when(receiveTask.getName()).thenReturn("My Receive Task");
        // Mock an empty message name
        org.camunda.bpm.model.bpmn.instance.Message msg = mock(org.camunda.bpm.model.bpmn.instance.Message.class);
        when(msg.getName()).thenReturn("");
        when(receiveTask.getMessage()).thenReturn(msg);

        List<BpmnElementValidationItem> issues = new ArrayList<>();

        // Act
        validator.validateReceiveTask(receiveTask, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty(), "Empty message name should yield a validation issue.");
    }
}