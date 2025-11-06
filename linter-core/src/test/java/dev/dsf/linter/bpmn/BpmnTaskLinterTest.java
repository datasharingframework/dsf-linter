package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.item.BpmnElementLintItem;
import dev.dsf.linter.output.item.BpmnServiceTaskNameEmptyLintItem;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.ReceiveTask;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * <p>
 * Test class for {@link BpmnTaskLinter}. Verifies the linting of ServiceTask, UserTask,
 * SendTask, and ReceiveTask elements.
 * </p>
 */
public class BpmnTaskLinterTest {

    private BpmnTaskLinter linter;
    private File mockBpmnFile;
    private static Path tempProjectRoot;

    @BeforeEach
    public void setUp() throws IOException {
        // Create a real temporary directory that will exist during tests
        tempProjectRoot = Files.createTempDirectory("test-project-root-");

        linter = new BpmnTaskLinter(tempProjectRoot.toFile());
        mockBpmnFile = new File("testProcess.bpmn");


        // Optionally create FHIR subdirectories to make it look more realistic
        Files.createDirectories(tempProjectRoot.resolve("fhir/ActivityDefinition"));
        Files.createDirectories(tempProjectRoot.resolve("fhir/StructureDefinition"));
        Files.createDirectories(tempProjectRoot.resolve("fhir/Questionnaire"));


    }


    /**
     * Cleanup method to delete the temporary directory after all tests.
     */
    @AfterAll
    public static void cleanup() throws IOException {
        if (tempProjectRoot != null && Files.exists(tempProjectRoot)) {
            // Recursively delete temporary directory
            deleteDirectory(tempProjectRoot.toFile());
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    /**
     * Tests {@link BpmnTaskLinter#lintServiceTask(ServiceTask, List, File, String)}
     * when the task has an empty name.
     */
    @Test
    public void testLintServiceTask_EmptyName() {
        // Arrange
        ServiceTask serviceTask = mock(ServiceTask.class);
        when(serviceTask.getId()).thenReturn("serviceTaskId");
        when(serviceTask.getName()).thenReturn("");
        when(serviceTask.getCamundaClass()).thenReturn("org.example.MyJavaDelegate");

        List<BpmnElementLintItem> issues = new ArrayList<>();

        // Act
        linter.lintServiceTask(serviceTask, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i instanceof BpmnServiceTaskNameEmptyLintItem),
                "We expect a BpmnServiceTaskNameEmptyLintItem for empty service task name.");
    }

    /**
     * Tests {@link BpmnTaskLinter#lintUserTask(UserTask, List, File, String)}
     * with an empty formKey.
     */
    @Test
    public void testLintUserTask_EmptyFormKey() {
        // Arrange
        UserTask userTask = mock(UserTask.class);
        when(userTask.getId()).thenReturn("userTaskId");
        when(userTask.getName()).thenReturn("Some User Task");
        when(userTask.getCamundaFormKey()).thenReturn("");

        List<BpmnElementLintItem> issues = new ArrayList<>();

        // Act
        linter.lintUserTask(userTask, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty(), "Empty formKey should yield a lint issue.");
    }

    /**
     * Tests {@link BpmnTaskLinter#lintSendTask(SendTask, List, File, String)} with a non-empty
     * name but no implementation class.
     */
    @Test
    public void testLintSendTask_NoImplementationClass() {
        // Arrange
        SendTask sendTask = mock(SendTask.class);
        when(sendTask.getId()).thenReturn("sendTaskId");
        when(sendTask.getName()).thenReturn("My Send Task");
        // No camundaClass set => return null or empty
        when(sendTask.getCamundaClass()).thenReturn(null);

        List<BpmnElementLintItem> issues = new ArrayList<>();

        // Act
        linter.lintSendTask(sendTask, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty(), "Expected a lint issue if implementation class is missing.");
    }

    /**
     * Tests {@link BpmnTaskLinter#lintReceiveTask(ReceiveTask, List, File, String)}
     * when the message name is empty.
     */
    @Test
    public void testLintReceiveTask_EmptyMessageName() {
        // Arrange
        ReceiveTask receiveTask = mock(ReceiveTask.class);
        when(receiveTask.getId()).thenReturn("receiveTaskId");
        when(receiveTask.getName()).thenReturn("My Receive Task");
        // Mock an empty message name
        org.camunda.bpm.model.bpmn.instance.Message msg = mock(org.camunda.bpm.model.bpmn.instance.Message.class);
        when(msg.getName()).thenReturn("");
        when(receiveTask.getMessage()).thenReturn(msg);

        List<BpmnElementLintItem> issues = new ArrayList<>();

        // Act
        linter.lintReceiveTask(receiveTask, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty(), "Empty message name should yield a lint issue.");
    }
}