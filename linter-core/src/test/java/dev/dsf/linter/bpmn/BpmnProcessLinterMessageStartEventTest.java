package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.BpmnElementLintItem;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Definitions;
import org.camunda.bpm.model.bpmn.instance.Message;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BpmnProcessLinterMessageStartEventTest {

    private BpmnProcessLinter processLinter;
    private File bpmnFile;

    @BeforeEach
    void setup() {
        processLinter = new BpmnProcessLinter(new File("."));
        bpmnFile = new File("test-process.bpmn");
    }

    @Test
    @DisplayName("Process without message start event => BPMN_MESSAGE_START_EVENT_NOT_FOUND")
    void processWithoutMessageStartReportsNotFound() {
        BpmnModelInstance model = Bpmn.createExecutableProcess("testorg_myprocess")
                .startEvent("start")
                .endEvent("end")
                .done();

        List<BpmnElementLintItem> issues = lintProcesses(model);

        assertTrue(issues.stream().anyMatch(i ->
                        i.getType() == LintingType.BPMN_MESSAGE_START_EVENT_NOT_FOUND
                                && i.getSeverity() == LinterSeverity.ERROR),
                "Expected BPMN_MESSAGE_START_EVENT_NOT_FOUND when process has only a generic start event");
    }

    @Test
    @DisplayName("Process with message start event => no NOT_FOUND")
    void processWithMessageStartDoesNotReportNotFound() {
        BpmnModelInstance model = Bpmn.createExecutableProcess("testorg_myprocess")
                .startEvent("start")
                .message("startMessage")
                .endEvent("end")
                .done();

        List<BpmnElementLintItem> issues = lintProcesses(model);

        assertFalse(issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_START_EVENT_NOT_FOUND),
                "Expected no BPMN_MESSAGE_START_EVENT_NOT_FOUND when process has a message start event");
    }

    @Test
    @DisplayName("ping.bpmn has message start on process level => no NOT_FOUND")
    void pingBpmnHasProcessLevelMessageStart() {
        File pingFile = new File("src/test/resources/bpmn/ping.bpmn");
        BpmnModelInstance model = Bpmn.readModelFromFile(pingFile);

        List<BpmnElementLintItem> issues = lintProcesses(model, pingFile);

        assertFalse(issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_START_EVENT_NOT_FOUND),
                "ping.bpmn should have a message start event on process level");
    }

    @Test
    @DisplayName("Message start only in subprocess => NOT_FOUND on process")
    void messageStartOnlyInSubProcessReportsNotFound() {
        BpmnModelInstance model = Bpmn.createEmptyModel();
        Definitions definitions = model.newInstance(Definitions.class);
        definitions.setTargetNamespace("http://camunda.org/examples");
        model.setDefinitions(definitions);

        Process process = model.newInstance(Process.class);
        process.setId("testorg_myprocess");
        process.setExecutable(true);
        definitions.addChildElement(process);

        StartEvent processStart = model.newInstance(StartEvent.class);
        processStart.setId("processStart");
        process.addChildElement(processStart);

        SubProcess subProcess = model.newInstance(SubProcess.class);
        subProcess.setId("sub");
        process.addChildElement(subProcess);

        StartEvent subStart = model.newInstance(StartEvent.class);
        subStart.setId("subMessageStart");
        subProcess.addChildElement(subStart);

        Message message = model.newInstance(Message.class);
        message.setId("Message_subStart");
        message.setName("subStartMessage");
        definitions.addChildElement(message);

        MessageEventDefinition messageDef = model.newInstance(MessageEventDefinition.class);
        messageDef.setMessage(message);
        subStart.addChildElement(messageDef);

        List<BpmnElementLintItem> issues = lintProcesses(model);

        assertTrue(issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_START_EVENT_NOT_FOUND),
                "Message start inside subprocess must not satisfy process-level requirement");
    }

    private List<BpmnElementLintItem> lintProcesses(BpmnModelInstance model) {
        return lintProcesses(model, bpmnFile);
    }

    private List<BpmnElementLintItem> lintProcesses(BpmnModelInstance model, File file) {
        List<BpmnElementLintItem> issues = new ArrayList<>();
        processLinter.lintProcesses(model, file, issues);
        return issues;
    }
}
