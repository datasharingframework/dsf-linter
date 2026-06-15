package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.BpmnElementLintItem;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmnProcessLinterVersionTagTest {

    @TempDir
    Path tempDir;

    private BpmnModelInstance createModelFromXml(String bpmnXml) {
        return Bpmn.readModelFromStream(
                new java.io.ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
    }

    private File createBpmnFile() throws IOException {
        Path bpmnPath = tempDir.resolve("process.bpmn");
        Files.writeString(bpmnPath, "<definitions/>");
        return bpmnPath.toFile();
    }

    private List<BpmnElementLintItem> lint(String processAttributes) throws IOException {
        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="test_process" isExecutable="true" %s>
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:endEvent id="EndEvent_1"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(processAttributes);

        BpmnModelInstance model = createModelFromXml(bpmnXml);
        BpmnProcessLinter linter = new BpmnProcessLinter(tempDir.toFile());
        List<BpmnElementLintItem> issues = new ArrayList<>();
        linter.lintProcesses(model, createBpmnFile(), issues);
        return issues;
    }

    @Test
    @DisplayName("Should report ERROR when camunda:versionTag is missing")
    void shouldReportErrorWhenVersionTagMissing() throws IOException {
        List<BpmnElementLintItem> issues = lint("");

        assertTrue(issues.stream().anyMatch(i ->
                        i.getSeverity() == LinterSeverity.ERROR
                                && i.getType() == LintingType.BPMN_PROCESS_VERSION_TAG_MISSING_OR_EMPTY),
                "Expected ERROR for missing camunda:versionTag");
    }

    @Test
    @DisplayName("Should report ERROR when camunda:versionTag is empty")
    void shouldReportErrorWhenVersionTagEmpty() throws IOException {
        List<BpmnElementLintItem> issues = lint("camunda:versionTag=\"\"");

        assertTrue(issues.stream().anyMatch(i ->
                        i.getSeverity() == LinterSeverity.ERROR
                                && i.getType() == LintingType.BPMN_PROCESS_VERSION_TAG_MISSING_OR_EMPTY),
                "Expected ERROR for empty camunda:versionTag");
    }

    @Test
    @DisplayName("Should report ERROR when camunda:versionTag is null literal")
    void shouldReportErrorWhenVersionTagNullLiteral() throws IOException {
        List<BpmnElementLintItem> issues = lint("camunda:versionTag=\"null\"");

        assertTrue(issues.stream().anyMatch(i ->
                        i.getSeverity() == LinterSeverity.ERROR
                                && i.getType() == LintingType.BPMN_PROCESS_VERSION_TAG_MISSING_OR_EMPTY),
                "Expected ERROR for camunda:versionTag='null'");
    }

    @Test
    @DisplayName("Should report WARN when camunda:versionTag has no placeholder")
    void shouldReportWarnWhenVersionTagHasNoPlaceholder() throws IOException {
        List<BpmnElementLintItem> issues = lint("camunda:versionTag=\"1.0.0\"");

        assertTrue(issues.stream().anyMatch(i ->
                        i.getSeverity() == LinterSeverity.WARN
                                && i.getType() == LintingType.BPMN_PROCESS_VERSION_TAG_NO_PLACEHOLDER),
                "Expected WARN for camunda:versionTag without '#{version}'");
    }

    @Test
    @DisplayName("Should report SUCCESS when camunda:versionTag uses placeholder")
    void shouldReportSuccessWhenVersionTagHasPlaceholder() throws IOException {
        List<BpmnElementLintItem> issues = lint("camunda:versionTag=\"#{version}\"");

        assertTrue(issues.stream().anyMatch(i ->
                        i.getSeverity() == LinterSeverity.SUCCESS
                                && i.getDescription().contains("camunda:versionTag uses '#{version}'")),
                "Expected SUCCESS for camunda:versionTag='#{version}'");
    }
}
