package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.BpmnElementLintItem;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BpmnMessageEventImplementationLinter}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Class-not-found path (both element types, both API versions)</li>
 *   <li>V2 MessageEndEvent: correct LintingType {@code BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING}</li>
 *   <li>V2 MessageIntermediateThrowEvent: correct LintingType
 *       {@code BPMN_INTERMEDIATE_THROW_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING} (regression guard for the bug
 *       where both element types erroneously used the end-event type)</li>
 *   <li>V1 MessageEndEvent: checks for {@code JavaDelegate} (ERROR) and
 *       {@code AbstractTaskMessageSend} extension (WARN)</li>
 *   <li>V1 MessageIntermediateThrowEvent: same V1 rules with element-specific WARN type</li>
 * </ul>
 * </p>
 */
class BpmnMessageEventImplementationLinterTest {

    /** A class that is guaranteed to be present in the JVM but implements no DSF interface. */
    private static final String WRONG_IMPL = "java.lang.String";

    /** A class name that will never be found on the classpath. */
    private static final String NONEXISTENT_CLASS = "com.example.nonexistent.GhostClass";

    private final File bpmnFile = new File("process.bpmn");

    @AfterEach
    void clearApiVersion() {
        ApiVersionHolder.clear();
    }

    // ==================== CLASS NOT FOUND ====================

    @Test
    void classNotFound_messageEndEvent_v2_reportsClassNotFound() {
        ApiVersionHolder.setVersion(ApiVersion.V2);
        File projectRoot = tempDir();

        List<BpmnElementLintItem> issues = lint(NONEXISTENT_CLASS, BpmnElementType.MESSAGE_END_EVENT, projectRoot);

        assertTrue(
                issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND),
                "Expected BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND for unknown class");
    }

    @Test
    void classNotFound_messageIntermediateThrowEvent_v2_reportsClassNotFound() {
        ApiVersionHolder.setVersion(ApiVersion.V2);
        File projectRoot = tempDir();

        List<BpmnElementLintItem> issues = lint(NONEXISTENT_CLASS, BpmnElementType.MESSAGE_INTERMEDIATE_THROW_EVENT, projectRoot);

        assertTrue(
                issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND),
                "Expected BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND for unknown class");
    }

    @Test
    void classNotFound_messageEndEvent_v1_reportsClassNotFound() {
        ApiVersionHolder.setVersion(ApiVersion.V1);
        File projectRoot = tempDir();

        List<BpmnElementLintItem> issues = lint(NONEXISTENT_CLASS, BpmnElementType.MESSAGE_END_EVENT, projectRoot);

        assertTrue(
                issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND),
                "Expected BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND for unknown class");
    }

    @Test
    void classNotFound_messageIntermediateThrowEvent_v1_reportsClassNotFound() {
        ApiVersionHolder.setVersion(ApiVersion.V1);
        File projectRoot = tempDir();

        List<BpmnElementLintItem> issues = lint(NONEXISTENT_CLASS, BpmnElementType.MESSAGE_INTERMEDIATE_THROW_EVENT, projectRoot);

        assertTrue(
                issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND),
                "Expected BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND for unknown class");
    }

    // ==================== V2: CORRECT LintingType PER ELEMENT TYPE ====================

    /**
     * Regression test: before the fix, both element types used
     * {@code BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING}. After the fix, end events use
     * that type and intermediate throw events use
     * {@code BPMN_INTERMEDIATE_THROW_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING}.
     */
    @Test
    void v2_messageEndEvent_wrongInterface_reportsEndEventType() {
        ApiVersionHolder.setVersion(ApiVersion.V2);

        List<BpmnElementLintItem> issues = lint(WRONG_IMPL, BpmnElementType.MESSAGE_END_EVENT, null);

        assertTrue(
                issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING),
                "MESSAGE_END_EVENT with wrong interface must report BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING");
        assertTrue(
                issues.stream().noneMatch(i -> i.getType() == LintingType.BPMN_INTERMEDIATE_THROW_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING),
                "MESSAGE_END_EVENT must NOT report BPMN_INTERMEDIATE_THROW_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING");
    }

    /**
     * Regression test: intermediate throw events must report their OWN type, not the end-event type.
     */
    @Test
    void v2_messageIntermediateThrowEvent_wrongInterface_reportsIntermediateThrowType() {
        ApiVersionHolder.setVersion(ApiVersion.V2);

        List<BpmnElementLintItem> issues = lint(WRONG_IMPL, BpmnElementType.MESSAGE_INTERMEDIATE_THROW_EVENT, null);

        assertTrue(
                issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_INTERMEDIATE_THROW_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING),
                "MESSAGE_INTERMEDIATE_THROW_EVENT with wrong interface must report BPMN_INTERMEDIATE_THROW_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING");
        assertTrue(
                issues.stream().noneMatch(i -> i.getType() == LintingType.BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING),
                "MESSAGE_INTERMEDIATE_THROW_EVENT must NOT report BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING");
    }

    @Test
    void v2_twoElementTypes_produceDifferentLintingTypes() {
        ApiVersionHolder.setVersion(ApiVersion.V2);

        List<BpmnElementLintItem> endEventIssues = lint(WRONG_IMPL, BpmnElementType.MESSAGE_END_EVENT, null);
        List<BpmnElementLintItem> throwEventIssues = lint(WRONG_IMPL, BpmnElementType.MESSAGE_INTERMEDIATE_THROW_EVENT, null);

        LintingType endEventType = endEventIssues.stream()
                .filter(i -> i.getType() == LintingType.BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING
                        || i.getType() == LintingType.BPMN_INTERMEDIATE_THROW_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING)
                .map(BpmnElementLintItem::getType)
                .findFirst().orElse(null);

        LintingType throwEventType = throwEventIssues.stream()
                .filter(i -> i.getType() == LintingType.BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING
                        || i.getType() == LintingType.BPMN_INTERMEDIATE_THROW_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING)
                .map(BpmnElementLintItem::getType)
                .findFirst().orElse(null);

        assertNotNull(endEventType, "End event must produce an interface-related LintingType");
        assertNotNull(throwEventType, "Intermediate throw event must produce an interface-related LintingType");
        assertNotEquals(endEventType, throwEventType,
                "End event and intermediate throw event must produce DIFFERENT LintingTypes for V2 wrong-interface errors");
    }

    // ==================== V1: JavaDelegate + AbstractTaskMessageSend checks ====================

    @Test
    void v1_messageEndEvent_wrongInterface_reportsJavaDelegateError() {
        ApiVersionHolder.setVersion(ApiVersion.V1);

        List<BpmnElementLintItem> issues = lint(WRONG_IMPL, BpmnElementType.MESSAGE_END_EVENT, null);

        assertTrue(
                issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_IMPLEMENTING_JAVA_DELEGATE),
                "V1 MessageEndEvent with wrong interface must report JavaDelegate not implemented");
    }

    @Test
    void v1_messageIntermediateThrowEvent_wrongInterface_reportsJavaDelegateError() {
        ApiVersionHolder.setVersion(ApiVersion.V1);

        List<BpmnElementLintItem> issues = lint(WRONG_IMPL, BpmnElementType.MESSAGE_INTERMEDIATE_THROW_EVENT, null);

        assertTrue(
                issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_IMPLEMENTING_JAVA_DELEGATE),
                "V1 MessageIntermediateThrowEvent with wrong interface must report JavaDelegate not implemented");
    }

    @Test
    void v1_messageEndEvent_classNotExtendingAbstract_reportsElementSpecificWarn() {
        ApiVersionHolder.setVersion(ApiVersion.V1);

        List<BpmnElementLintItem> issues = lint(WRONG_IMPL, BpmnElementType.MESSAGE_END_EVENT, null);

        assertTrue(
                issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_END_EVENT_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_TASK_MESSAGE_SEND),
                "V1 MessageEndEvent must warn when class does not extend AbstractTaskMessageSend");
    }

    @Test
    void v1_messageIntermediateThrowEvent_classNotExtendingAbstract_reportsElementSpecificWarn() {
        ApiVersionHolder.setVersion(ApiVersion.V1);

        List<BpmnElementLintItem> issues = lint(WRONG_IMPL, BpmnElementType.MESSAGE_INTERMEDIATE_THROW_EVENT, null);

        assertTrue(
                issues.stream().anyMatch(i -> i.getType() == LintingType.BPMN_MESSAGE_INTERMEDIATE_THROW_EVENT_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_TASK_MESSAGE_SEND),
                "V1 MessageIntermediateThrowEvent must warn when class does not extend AbstractTaskMessageSend");
    }

    @Test
    void v1_endEventAndThrowEvent_produceDifferentAbstractClassWarnTypes() {
        ApiVersionHolder.setVersion(ApiVersion.V1);

        List<BpmnElementLintItem> endEventIssues = lint(WRONG_IMPL, BpmnElementType.MESSAGE_END_EVENT, null);
        List<BpmnElementLintItem> throwEventIssues = lint(WRONG_IMPL, BpmnElementType.MESSAGE_INTERMEDIATE_THROW_EVENT, null);

        LintingType endEventWarn = endEventIssues.stream()
                .filter(i -> i.getType() == LintingType.BPMN_MESSAGE_END_EVENT_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_TASK_MESSAGE_SEND
                        || i.getType() == LintingType.BPMN_MESSAGE_INTERMEDIATE_THROW_EVENT_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_TASK_MESSAGE_SEND)
                .map(BpmnElementLintItem::getType)
                .findFirst().orElse(null);

        LintingType throwEventWarn = throwEventIssues.stream()
                .filter(i -> i.getType() == LintingType.BPMN_MESSAGE_END_EVENT_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_TASK_MESSAGE_SEND
                        || i.getType() == LintingType.BPMN_MESSAGE_INTERMEDIATE_THROW_EVENT_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_TASK_MESSAGE_SEND)
                .map(BpmnElementLintItem::getType)
                .findFirst().orElse(null);

        assertNotNull(endEventWarn, "End event must have an abstract-class-warn LintingType");
        assertNotNull(throwEventWarn, "Intermediate throw event must have an abstract-class-warn LintingType");
        assertNotEquals(endEventWarn, throwEventWarn,
                "V1 abstract class warn must differ between end event and intermediate throw event");
    }

    // ==================== Helpers ====================

    private List<BpmnElementLintItem> lint(String implClass, BpmnElementType elementType, File projectRoot) {
        List<BpmnElementLintItem> issues = new ArrayList<>();
        String processId = "test_process";
        String elementId = "element_1";
        BpmnMessageEventImplementationLinter.lintMessageEventImplementationClass(
                implClass, elementId, elementType, issues, bpmnFile, processId,
                ApiVersionHolder.getVersion(), projectRoot);
        return issues;
    }

    private static File tempDir() {
        try {
            Path p = Files.createTempDirectory("linter-test-");
            p.toFile().deleteOnExit();
            return p.toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
