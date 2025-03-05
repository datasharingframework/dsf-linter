package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.item.BpmnElementValidationItem;
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
 * Test class for {@link BpmnSubProcessValidator}. Ensures the validation logic for
 * multi-instance sub processes is handled correctly.
 * </p>
 */
class BpmnSubProcessValidatorTest {

    private BpmnSubProcessValidator validator;
    private File mockBpmnFile;

    @BeforeEach
    void setUp() {
        validator = new BpmnSubProcessValidator(new File("mockProjectRoot"));
        mockBpmnFile = new File("testProcess.bpmn");
    }

    /**
     * Tests {@link BpmnSubProcessValidator#validateSubProcess(SubProcess, List, File, String)}
     * with a multi-instance loop characteristic that does not set camunda:asyncBefore=true.
     */
    @Test
    void testValidateSubProcess_MultiInstance_NoAsyncBefore() {
        // Arrange
        SubProcess subProcess = mock(SubProcess.class);
        when(subProcess.getId()).thenReturn("subProcessId");

        MultiInstanceLoopCharacteristics multiInstance = mock(MultiInstanceLoopCharacteristics.class);
        // camundaAsyncBefore is false by default in the real model, we mock it here.
        when(multiInstance.isCamundaAsyncBefore()).thenReturn(false);

        when(subProcess.getLoopCharacteristics()).thenReturn(multiInstance);

        List<BpmnElementValidationItem> issues = new ArrayList<>();

        // Act
        validator.validateSubProcess(subProcess, issues, mockBpmnFile, "ProcessId");

        // Assert
        assertFalse(issues.isEmpty(), "Expected a validation issue if asyncBefore is not set to true.");
    }
}
