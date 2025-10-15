package dev.dsf.linter.bpmn;

import dev.dsf.linter.item.BpmnElementValidationItem;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test suite for the {@link BpmnModelValidator}.
 * <p>
 * This class contains unit tests to verify the correct behavior of the BPMN model validator.
 * It uses various BPMN model configurations created with the Camunda BPM Model API to simulate
 * common misconfigurations such as missing names, missing implementations, and misconfigured elements.
 * Each test method builds a specific BPMN model and asserts that the validator detects the expected issues.
 * </p>
 * <p>
 * For more details on JavaDoc comments and standards, please refer to the
 * <a href="https://docs.oracle.com/en/java/javase/11/docs/specs/javadoc/doc-comment-spec.html">Oracle Javadoc Specification</a>.
 * </p>
 */
class TestBpmnModelValidator {

    private BpmnModelValidator validator;
    private File dummyBpmnFile; // Reference file passed into the validator
    private String dummyProcessId;

    /**
     * Setup method executed before each test.
     * <p>
     * This method initializes the {@link BpmnModelValidator} with a dummy project root used to simulate.
     * It also sets up a dummy BPMN file reference and a process ID that will be used in subsequent tests.
     * </p>
     */
    @BeforeEach
    void setup() {
        // Create a new validator with an explicit project root for class loading
        validator = new BpmnModelValidator(new File("."));

        // We'll pretend our BPMN is in "dummyFile.bpmn"
        dummyBpmnFile = new File("dummyFile.bpmn");
        dummyProcessId = "TestProcess";
    }

    /**
     * Tests a ServiceTask with no name and no implementation.
     * <p>
     * This test creates a BPMN model with a ServiceTask that lacks a name and an implementation class.
     * It asserts that the validator returns at least one validation issue for this misconfiguration.
     * </p>
     */
    @Test
    @DisplayName("1) ServiceTask: No name, no implementation => expect validation issues")
    void testServiceTaskNoNameNoImplementation() {
        BpmnModelInstance model = Bpmn.createExecutableProcess(dummyProcessId)
                .startEvent("start")
                .serviceTask("serviceTaskNoNameNoClass") // no .name() => name is empty
                .endEvent("end")
                .done();

        List<BpmnElementValidationItem> issues = validator.validateModel(model, dummyBpmnFile);

        // We expect at least one issue (e.g., "no name", "no implementation class")
        assertFalse(issues.isEmpty(), "Expected validation issues for a ServiceTask with no name / no impl");
    }

    /**
     * Tests a StartEvent with a MessageEventDefinition that has no message name.
     * <p>
     * This test creates a BPMN model with a Message Start Event where the associated message name is empty.
     * The test expects the validator to return at least one validation issue for the misconfiguration.
     * </p>
     */
    @Test
    @DisplayName("2) StartEvent with MessageEventDefinition but no message name => expect issue")
    void testMessageStartEventNoMessageName() {
        BpmnModelInstance model = Bpmn.createExecutableProcess(dummyProcessId)
                .startEvent("messageStart")
                .name("Message Start Event")
                .message("messageWithoutName") // not an actual name
                .endEvent("end")
                .done();

        // Optionally remove the message's name if the builder sets it
        MessageEventDefinition msgDef = model.getModelElementById("messageStartEventDefinition");
        if (msgDef != null && msgDef.getMessage() != null) {
            msgDef.getMessage().setName("");
        }

        List<BpmnElementValidationItem> issues = validator.validateModel(model, dummyBpmnFile);
        assertFalse(issues.isEmpty(), "We expect an issue for a message start event with empty message name");
    }

    /**
     * Tests a BoundaryEvent with an ErrorEventDefinition that has no error code.
     * <p>
     * This test creates a BPMN model where a BoundaryEvent is configured with an ErrorEventDefinition
     * but the associated error does not have an error code and its name is empty.
     * The validator is expected to detect this misconfiguration and return a validation issue.
     * </p>
     */
    @Test
    @DisplayName("3) BoundaryEvent with ErrorEventDefinition but no error code => expect error")
    void testErrorBoundaryEventNoErrorCode() {
        BpmnModelInstance model = Bpmn.createExecutableProcess(dummyProcessId)
                .startEvent("start")
                .userTask("someTask")
                .name("User Task For Boundary")
                .boundaryEvent("errorBoundary")
                .error() // might create a bpmn:error with no code
                .endEvent("end")
                .done();

        // Force errorRef's code to be null and name empty
        BoundaryEvent boundary = model.getModelElementById("errorBoundary");
        if (boundary != null) {
            ErrorEventDefinition errDef = (ErrorEventDefinition) boundary.getEventDefinitions().iterator().next();
            if (errDef.getError() != null) {
                errDef.getError().setErrorCode(null);
                errDef.getError().setName("");
            }
        }

        List<BpmnElementValidationItem> issues = validator.validateModel(model, dummyBpmnFile);
        assertFalse(issues.isEmpty(), "Should have an issue about missing error code on boundary event");
    }

    /**
     * Tests an ExclusiveGateway with multiple SequenceFlows lacking names.
     * <p>
     * This test builds a BPMN model where an ExclusiveGateway is connected to multiple SequenceFlows,
     * all of which do not have names. The validator is expected to generate warnings regarding the unnamed flows.
     * </p>
     */
    @Test
    @DisplayName("4) ExclusiveGateway with multiple SequenceFlows => unnamed flows => expect warnings")
    void testExclusiveGatewayMultipleFlowsNoNames() {
        BpmnModelInstance model = Bpmn.createExecutableProcess(dummyProcessId)
                .startEvent("start")
                .exclusiveGateway("gateway")
                .sequenceFlowId("flow1").condition("cond1", "#{someCondition == true}") // no .name()
                .endEvent("end1")
                .moveToLastGateway()
                .sequenceFlowId("flow2") // no .name()
                .endEvent("end2")
                .done();

        List<BpmnElementValidationItem> issues = validator.validateModel(model, dummyBpmnFile);

        // We expect multiple flows from the ExclusiveGateway to trigger warnings for unnamed flows
        assertFalse(issues.isEmpty(),
                "We have multiple flows from an ExclusiveGateway => we expect some warnings for unnamed flows");
    }

    /**
     * Tests a multi-instance SubProcess without asyncBefore configuration.
     * <p>
     * This test creates a BPMN model with a multi-instance SubProcess that lacks the asyncBefore setting.
     * It constructs a nested BPMN structure including a main process and an inner SubProcess with its own flow.
     * The validator is expected to detect the missing asyncBefore configuration for the multi-instance SubProcess.
     * </p>
     */
    @Test
    @DisplayName("5) SubProcess with multi-instance but not asyncBefore => expect a validation item")
    void testMultiInstanceSubProcessNotAsyncBefore() {
        // Create an empty model with definitions
        BpmnModelInstance model = Bpmn.createEmptyModel();
        Definitions definitions = model.newInstance(Definitions.class);
        definitions.setTargetNamespace("http://camunda.org/examples");
        model.setDefinitions(definitions);

        // Create the main process
        Process process = model.newInstance(Process.class);
        process.setId(dummyProcessId);
        process.setExecutable(true);
        definitions.addChildElement(process);

        // Add a StartEvent to the process
        StartEvent startEvent = model.newInstance(StartEvent.class);
        startEvent.setId("start");
        process.addChildElement(startEvent);

        // Create a multi-instance SubProcess (without asyncBefore)
        SubProcess subProcess = model.newInstance(SubProcess.class);
        subProcess.setId("mySubProcess");
        MultiInstanceLoopCharacteristics mi = model.newInstance(MultiInstanceLoopCharacteristics.class);
        subProcess.setLoopCharacteristics(mi);
        process.addChildElement(subProcess);

        // Add an EndEvent to the process
        EndEvent endEvent = model.newInstance(EndEvent.class);
        endEvent.setId("end");
        process.addChildElement(endEvent);

        // Connect StartEvent -> SubProcess and SubProcess -> EndEvent via SequenceFlows
        SequenceFlow flow1 = model.newInstance(SequenceFlow.class);
        flow1.setId("flow1");
        flow1.setSource(startEvent);
        flow1.setTarget(subProcess);
        process.addChildElement(flow1);

        SequenceFlow flow2 = model.newInstance(SequenceFlow.class);
        flow2.setId("flow2");
        flow2.setSource(subProcess);
        flow2.setTarget(endEvent);
        process.addChildElement(flow2);

        // Inside the SubProcess, add a StartEvent and an EndEvent connected by a SequenceFlow
        StartEvent subStart = model.newInstance(StartEvent.class);
        subStart.setId("subStart");
        subProcess.addChildElement(subStart);

        EndEvent subEnd = model.newInstance(EndEvent.class);
        subEnd.setId("subEnd");
        subProcess.addChildElement(subEnd);

        SequenceFlow subFlow = model.newInstance(SequenceFlow.class);
        subFlow.setId("subFlow");
        subFlow.setSource(subStart);
        subFlow.setTarget(subEnd);
        subProcess.addChildElement(subFlow);

        // Validate the model and check for multi-instance misconfiguration
        List<BpmnElementValidationItem> issues = validator.validateModel(model, dummyBpmnFile);

        // Typically, we expect an issue regarding the multi-instance SubProcess not having asyncBefore enabled
        assertFalse(issues.isEmpty(),
                "Should detect that multi-instance SubProcess is not asyncBefore if your validator enforces that");
    }

    /**
     * Tests a UserTask with no name.
     * <p>
     * This test creates a BPMN model containing a UserTask that does not have a name.
     * The validator is expected to return at least one validation issue for the missing name.
     * </p>
     */
    @Test
    @DisplayName("6) UserTask with no name => expect validation issue")
    void testUserTaskNoName() {
        BpmnModelInstance model = Bpmn.createExecutableProcess(dummyProcessId)
                .startEvent("start")
                .userTask("userTaskNoName") // no .name()
                .endEvent("end")
                .done();

        List<BpmnElementValidationItem> issues = validator.validateModel(model, dummyBpmnFile);

        // We expect at least one issue for the missing name on the UserTask
        assertFalse(issues.isEmpty(), "Expect a missing name issue");
    }

    /**
     * Tests a SendTask with no name.
     * <p>
     * This test creates a BPMN model with a SendTask that lacks a name.
     * The validator is expected to detect this configuration error and return a validation issue.
     * </p>
     */
    @Test
    @DisplayName("7) SendTask with no name => expect event name empty issue")
    void testSendTaskNoName() {
        BpmnModelInstance model = Bpmn.createExecutableProcess(dummyProcessId)
                .startEvent("start")
                .sendTask("sendTaskNoName") // no .name()
                .endEvent("end")
                .done();

        List<BpmnElementValidationItem> issues = validator.validateModel(model, dummyBpmnFile);

        // We expect an issue regarding the missing name on the SendTask
        assertFalse(issues.isEmpty(), "Should mention that sendTaskNoName has no name");
    }
}
