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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>
 * This test class exercises the {@link BpmnFieldInjectionLinter} utility methods to lint
 * {@code <camunda:field>} elements in BPMN elements. It tests both scenarios where the fields
 * are provided as valid string literals and scenarios where lint errors should be reported.
 * </p>
 *
 * <p>
 * References:
 * <ul>
 *   <li><a href="https://junit.org/junit5/docs/current/user-guide/">JUnit 5 Documentation</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/model-api/bpmn-model-api/">
 *       Camunda BPMN Model API</a></li>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 * </ul>
 * </p>
 */
public class BpmnFieldInjectionLinterTest
{

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

    /**
     * A sample project root reference (used in linting to locate FHIR resources).
     * In a real scenario, this could point to your project directory.
     */
    private static File projectRoot;

    /**
     * Setup method to initialize {@code bpmnFile} and {@code projectRoot}
     * before any tests are run.
     */
    @BeforeAll
    public static void init()
    {
        bpmnFile = Paths.get("dummy-process.bpmn").toFile();
        projectRoot = Paths.get("dummy-project-root").toFile();
    }

    /**
     * Tests that when no {@code <camunda:field>} elements exist, there are no lint issues.
     */
    @Test
    @DisplayName("Test no camunda:field elements => no issues")
    public void testNoFieldsNoIssues()
    {
        // Build a minimal BPMN model
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

    /**
     * Tests that providing a non-string-literal (e.g., an expression) triggers a lint issue.
     */
    @Test
    @DisplayName("Test camunda:field with expression => triggers NotStringLiteral issue")
    public void testFieldWithExpressionTriggersError()
    {
        // Build a BPMN model with a ServiceTask that has a <camunda:field name="profile" camunda:expression="..."/>
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
        assertInstanceOf(BpmnFieldInjectionNotStringLiteralLintItem.class, issues.get(0), "Expected the issue to be an instance of BpmnFieldInjectionNotStringLiteralLintItem");
    }

    /**
     * Tests that providing a valid string literal (via {@code camunda:string}) does not trigger
     * the NotStringLiteral lint item, but may trigger other checks if the name is "messageName"
     * and the value is empty.
     */
    @Test
    @DisplayName("Test camunda:field with literal => no NotStringLiteral issue, but may trigger others if empty")
    public void testFieldWithLiteral()
    {
        // Build a BPMN model with a ServiceTask that has a <camunda:field name="messageName"><camunda:string></camunda:string></camunda:field>
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

    /**
     * Tests that an unknown field name (not "profile", "messageName", or "instantiatesCanonical")
     * triggers an appropriate unknown field injection lint item.
     */
    @Test
    @DisplayName("Test unknown field name => triggers an unknown field injection issue")
    public void testUnknownFieldName()
    {
        // Build a BPMN model with a ServiceTask that has a <camunda:field name="someUnknownName" camunda:stringValue="test"/>
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
        // We expect a BpmnUnknownFieldInjectionLintItem, if present in your codebase.
    }

    /**
     * Tests that an {@code EndEvent} with a nested {@code MessageEventDefinition} containing
     * {@code <camunda:field>} elements also triggers linting.
     */
    @Test
    @DisplayName("Test EndEvent with MessageEventDefinition => fields get linted")
    public void testEndEventMessageFields()
    {
        // Build a BPMN model that ends with a MessageEventDefinition
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
        assertInstanceOf(BpmnFieldInjectionMessageValueEmptyLintItem.class, issues.get(0), "Expected the issue to be an instance of BpmnFieldInjectionMessageValueEmptyLintItem");
    }
}
