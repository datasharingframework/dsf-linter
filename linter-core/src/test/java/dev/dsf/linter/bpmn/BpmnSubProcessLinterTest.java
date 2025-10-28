package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.item.BpmnElementLintItem;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

/**
 * <p>
 * Test class for {@link BpmnSubProcessLinter}. Ensures the linting logic for
 * multi-instance sub processes is handled correctly.
 * </p>
 */
class BpmnSubProcessLinterTest {

    private BpmnSubProcessLinter linter;
    private File mockBpmnFile;

    @BeforeEach
    public void setUp() {
        linter = new BpmnSubProcessLinter(new File("mockProjectRoot"));
        mockBpmnFile = new File("testProcess.bpmn");
    }

    /**
     * Tests {@link BpmnSubProcessLinter#lintSubProcess(SubProcess, List, File, String)}
     * with a multi-instance loop characteristic that does not set camunda:asyncBefore=true.
     */
    @Test
    public void testLintSubProcess_MultiInstance_NoAsyncBefore() {
        // Arrange
        SubProcess subProcess = mock(SubProcess.class);
        when(subProcess.getId()).thenReturn("subProcessId");

        MultiInstanceLoopCharacteristics multiInstance = mock(MultiInstanceLoopCharacteristics.class);
        // camundaAsyncBefore is false by default in the real model, we mock it here.
        when(multiInstance.isCamundaAsyncBefore()).thenReturn(false);

        when(subProcess.getLoopCharacteristics()).thenReturn(multiInstance);

        List<BpmnElementLintItem> issues = new ArrayList<>();

        // Act
        linter.lintSubProcess(subProcess, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty(), "Expected a lint issue if asyncBefore is not set to true.");
    }
}
