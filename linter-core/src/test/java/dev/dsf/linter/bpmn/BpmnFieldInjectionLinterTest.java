package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.BpmnElementLintItem;
import dev.dsf.linter.output.item.BpmnFieldInjectionMessageValueEmptyLintItem;
import dev.dsf.linter.output.item.BpmnFieldInjectionNotStringLiteralLintItem;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaField;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaString;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for BpmnFieldInjectionLinter utility methods.
 * Uses JUnit 5's temporary directory support for realistic testing.
 */
public class BpmnFieldInjectionLinterTest {

    private static List<BpmnElementLintItem> nonSuccess(List<BpmnElementLintItem> items) {
        List<BpmnElementLintItem> out = new ArrayList<>();
        for (BpmnElementLintItem it : items) {
            try {
                if (it.getSeverity() != LinterSeverity.SUCCESS) {
                    out.add(it);
                }
            } catch (Throwable ignored) {
                out.add(it); // fail-open if getSeverity is unavailable
            }
        }
        return out;
    }

    /**
     * A sample BPMN file reference (not actually used to load from disk here).
     * In a real scenario, this might point to a file containing the BPMN.
     */
    private static File bpmnFile;
    private static Path tempProjectRoot;
    private static File projectRoot;

    /**
     * Setup method to create a temporary project root directory before tests.
     * This ensures FileSystemResourceProvider validation passes.
     */
    @BeforeAll
    public static void init() throws IOException {
        bpmnFile = Paths.get("dummy-process.bpmn").toFile();

        // Create a real temporary directory that will exist during tests
        tempProjectRoot = Files.createTempDirectory("test-project-root-");
        projectRoot = tempProjectRoot.toFile();

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
                    file.delete();// Ignore deletion failures in test cleanup
                }
            }
        }
        directory.delete();
    }

    @Test
    @DisplayName("Test no camunda:field elements => no issues")
    public void testNoFieldsNoIssues() {
        BpmnModelInstance model = Bpmn
                .createProcess("testProcess")
                .startEvent()
                .serviceTask("serviceTask")
                .endEvent()
                .done();

        // Retrieve the ServiceTask as the BaseElement
        ServiceTask serviceTask = model.getModelElementById("serviceTask");

        List<BpmnElementLintItem> issues = new ArrayList<>();
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                serviceTask, issues, bpmnFile, "testProcess", projectRoot
        );

        assertTrue(issues.isEmpty(), "Expected no lint issues when no fields are present");
    }

    @Test
    @DisplayName("Test camunda:field with expression => triggers NotStringLiteral issue")
    public void testFieldWithExpressionTriggersError() {
        BpmnModelInstance model = Bpmn
                .createProcess("testProcessExpressions")
                .startEvent()
                .serviceTask("serviceTaskWithExpr")
                .endEvent()
                .done();

        // Manually add an extension element with a CamundaField using an expression
        ServiceTask serviceTask = model.getModelElementById("serviceTaskWithExpr");

        // 1) Create <bpmn:extensionElements> node (older approach)
        ExtensionElements extensionElements = model.newInstance(ExtensionElements.class);
        // For older Camunda versions, manually attach it:
        serviceTask.addChildElement(extensionElements);

        // 2) Create the field, set expression, and add to extensionElements
        CamundaField field = model.newInstance(CamundaField.class);
        field.setCamundaName("profile");
        field.setCamundaExpression("${someExpression}");
        extensionElements.addChildElement(field);

        // Now lint
        List<BpmnElementLintItem> issues = new ArrayList<>();
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                serviceTask, issues, bpmnFile, "testProcessExpressions", projectRoot
        );

        // We expect exactly 1 issue => BpmnFieldInjectionNotStringLiteralLintItem
        assertEquals(1, issues.size(), "Expected exactly one lint issue for an expression-based field");
        assertInstanceOf(BpmnFieldInjectionNotStringLiteralLintItem.class, issues.get(0),
                "Expected the issue to be an instance of BpmnFieldInjectionNotStringLiteralLintItem");
    }

    @Test
    @DisplayName("Test camunda:field with literal => no NotStringLiteral issue, but may trigger others if empty")
    public void testFieldWithLiteral() {
        BpmnModelInstance model = Bpmn
                .createProcess("testProcessLiteral")
                .startEvent()
                .serviceTask("serviceTaskWithLiteral")
                .endEvent()
                .done();

        // Retrieve the ServiceTask
        ServiceTask serviceTask = model.getModelElementById("serviceTaskWithLiteral");

        // 1) Create <bpmn:extensionElements> node
        ExtensionElements extensionElements = model.newInstance(ExtensionElements.class);
        serviceTask.addChildElement(extensionElements);

        // 2) Create the field with name="messageName" but with an empty literal
        CamundaField field = model.newInstance(CamundaField.class);
        field.setCamundaName("messageName");

        // Use <camunda:string> as a nested element (empty => should trigger the "empty messageName" lint item)
        CamundaString stringElement = model.newInstance(CamundaString.class);
        field.addChildElement(stringElement);

        extensionElements.addChildElement(field);

        // lint
        File bpmnFile = new File("testProcessLiteral.bpmn");
        List<BpmnElementLintItem> issues = new ArrayList<>();
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                serviceTask, issues, bpmnFile, "testProcessLiteral", projectRoot
        );

        // We expect exactly 1 non-success issue (the empty messageName). SUCCESS items (e.g., "provided as string literal")
        // are intentionally ignored.
        List<BpmnElementLintItem> nonSuccess = nonSuccess(issues);
        assertEquals(1, nonSuccess.size(), "Expected exactly one non-success issue for empty messageName");
        assertTrue(
                nonSuccess.stream().anyMatch(i -> i instanceof BpmnFieldInjectionMessageValueEmptyLintItem),
                "Expected the issue to be an instance of BpmnFieldInjectionMessageValueEmptyLintItem"
        );
    }

    @Test
    @DisplayName("Test unknown field name => triggers an unknown field injection issue")
    public void testUnknownFieldName() {
        BpmnModelInstance model = Bpmn
                .createProcess("testProcessUnknown")
                .startEvent()
                .serviceTask("serviceTaskUnknownField")
                .endEvent()
                .done();

        ServiceTask serviceTask = model.getModelElementById("serviceTaskUnknownField");

        // 1) Create <bpmn:extensionElements> node
        ExtensionElements extensionElements = model.newInstance(ExtensionElements.class);
        serviceTask.addChildElement(extensionElements);

        // 2) Create field with unknown name
        CamundaField field = model.newInstance(CamundaField.class);
        field.setCamundaName("someUnknownName");
        field.setCamundaStringValue("test");
        extensionElements.addChildElement(field);

        // Lint
        List<BpmnElementLintItem> issues = new ArrayList<>();
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                serviceTask, issues, bpmnFile, "testProcessUnknown", projectRoot
        );

        // Filter out SUCCESS items to get only actual lint errors
        List<BpmnElementLintItem> nonSuccess = nonSuccess(issues);
        assertEquals(1, nonSuccess.size(), "Expected exactly one non-success lint issue for unknown field name");
    }

    @Test
    @DisplayName("Test EndEvent with MessageEventDefinition => fields get linted")
    public void testEndEventMessageFields() {
        BpmnModelInstance model = Bpmn
                .createProcess("testProcessEndEvent")
                .startEvent()
                .endEvent("theEndEvent")
                .messageEventDefinition("msgDef")
                .done();

        // Retrieve the EndEvent
        EndEvent endEvent = model.getModelElementById("theEndEvent");

        // Also retrieve the MessageEventDefinition by its ID
        MessageEventDefinition messageDef = model.getModelElementById("msgDef");

        // 1) Create <bpmn:extensionElements> node
        ExtensionElements extensionElements = model.newInstance(ExtensionElements.class);
        messageDef.addChildElement(extensionElements);

        // 2) Add a field with an empty literal for messageName
        CamundaField field = model.newInstance(CamundaField.class);
        field.setCamundaName("messageName");
        field.setCamundaStringValue(""); // empty => triggers lint item
        extensionElements.addChildElement(field);

        // Now lint
        List<BpmnElementLintItem> issues = new ArrayList<>();
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                endEvent, issues, bpmnFile, "testProcessEndEvent", projectRoot
        );

        assertEquals(1, issues.size(),
                "Expected one issue due to empty messageName in nested MessageEventDefinition");
        assertInstanceOf(BpmnFieldInjectionMessageValueEmptyLintItem.class, issues.get(0),
                "Expected the issue to be an instance of BpmnFieldInjectionMessageValueEmptyLintItem");
    }
}