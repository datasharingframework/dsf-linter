package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersion;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.classloading.ClassInspector.*;
import static dev.dsf.linter.constants.DsfApiConstants.*;

/**
 * Utility class for validating implementation classes for message events.
 * <p>
 * Validates implementation classes for BPMN Message Intermediate Throw Events and Message End Events
 * against the correct DSF API interface requirements per API version:
 * </p>
 * <ul>
 *   <li><b>V1:</b> Implementation class should extend {@code AbstractTaskMessageSend} (WARN if not)
 *       and must implement {@code JavaDelegate} (ERROR if not).</li>
 *   <li><b>V2 MessageEndEvent:</b> Implementation class must implement {@code MessageEndEvent}.</li>
 *   <li><b>V2 MessageIntermediateThrowEvent:</b> Implementation class must implement
 *       {@code MessageIntermediateThrowEvent}.</li>
 * </ul>
 */
public final class BpmnMessageEventImplementationLinter {

    private BpmnMessageEventImplementationLinter() {
        // Utility class - no instantiation
    }

    /**
     * Validates the implementation class for a BPMN Message Event (Intermediate Throw or End Event).
     *
     * <p>For V1, checks that the class extends {@code AbstractTaskMessageSend} (warning) and
     * implements {@code JavaDelegate} (error). For V2, checks that the class implements the
     * element-type-specific DSF activity interface.</p>
     *
     * @param implClass   the fully-qualified implementation class name to validate
     * @param elementId   the BPMN element identifier
     * @param elementType the BPMN element type ({@code MESSAGE_END_EVENT} or
     *                    {@code MESSAGE_INTERMEDIATE_THROW_EVENT})
     * @param issues      list to which lint items will be added
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the BPMN process identifier
     * @param apiVersion  the DSF API version
     * @param projectRoot the project root directory
     */
    public static void lintMessageEventImplementationClass(
            String implClass,
            String elementId,
            BpmnElementType elementType,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            ApiVersion apiVersion,
            File projectRoot) {

        // Step 1: Check class existence
        if (!classExists(implClass, projectRoot)) {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR,
                    LintingType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                    elementId, bpmnFile, processId,
                    "Implementation class not found: " + implClass));
            return;
        }

        // Step 2: Version-specific interface validation
        if (apiVersion == ApiVersion.V1) {
            lintV1MessageEventImplementation(implClass, elementId, elementType, issues, bpmnFile, processId, projectRoot);
        } else if (apiVersion == ApiVersion.V2) {
            lintV2MessageEventImplementation(implClass, elementId, elementType, issues, bpmnFile, processId, projectRoot);
        }
        //Handling of default or unknown API versions is intentionally omitted.
        // This point is unreachable because the API version is validated at an earlier entry point;
        // if unknown, an exception is thrown, and the linter skips validation to move on to the next plugin.
    }

    /**
     * V1 validation: checks AbstractTaskMessageSend extension (WARN) and JavaDelegate
     * implementation (ERROR). This mirrors the pattern used by
     * {@code BpmnTaskLinter.lintSendTask} for V1 Send Tasks.
     */
    private static void lintV1MessageEventImplementation(
            String implClass,
            String elementId,
            BpmnElementType elementType,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        boolean extendsAbstract = isSubclassOf(implClass, V1_ABSTRACT_TASK_MESSAGE_SEND, projectRoot);
        LintingType notExtendingType = elementType == BpmnElementType.MESSAGE_END_EVENT
                ? LintingType.BPMN_MESSAGE_END_EVENT_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_TASK_MESSAGE_SEND
                : LintingType.BPMN_MESSAGE_INTERMEDIATE_THROW_EVENT_IMPLEMENTATION_CLASS_NOT_EXTENDING_ABSTRACT_TASK_MESSAGE_SEND;

        if (extendsAbstract) {
            issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                    "Implementation class '" + implClass + "' extends " + getSimpleName(V1_ABSTRACT_TASK_MESSAGE_SEND) + "."));
        } else {
            issues.add(new BpmnElementLintItem(LinterSeverity.WARN, notExtendingType,
                    elementId, bpmnFile, processId,
                    "Implementation class '" + implClass + "' does not extend '"
                            + getSimpleName(V1_ABSTRACT_TASK_MESSAGE_SEND) + "'."));
        }

        boolean implementsDelegate = implementsInterface(implClass, V1_JAVA_DELEGATE, projectRoot);

        if (implementsDelegate) {
            issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                    "Implementation class '" + implClass + "' implements " + getSimpleName(V1_JAVA_DELEGATE) + "."));
        } else {
            issues.add(new BpmnElementLintItem(
                    LinterSeverity.ERROR,
                    LintingType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_IMPLEMENTING_JAVA_DELEGATE,
                    elementId, bpmnFile, processId,
                    "Implementation class '" + implClass + "' does not implement '"
                            + getSimpleName(V1_JAVA_DELEGATE) + "'."));
        }
    }

    /**
     * V2 validation: checks the element-type-specific DSF activity interface.
     * <ul>
     *   <li>MESSAGE_END_EVENT → must implement {@code dev.dsf.bpe.v2.activity.MessageEndEvent}</li>
     *   <li>MESSAGE_INTERMEDIATE_THROW_EVENT → must implement
     *       {@code dev.dsf.bpe.v2.activity.MessageIntermediateThrowEvent}</li>
     * </ul>
     */
    private static void lintV2MessageEventImplementation(
            String implClass,
            String elementId,
            BpmnElementType elementType,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        String expectedInterface = elementType == BpmnElementType.MESSAGE_END_EVENT
                ? V2_MESSAGE_END_EVENT
                : V2_MESSAGE_INTERMEDIATE_THROW;

        LintingType notImplementingType = elementType == BpmnElementType.MESSAGE_END_EVENT
                ? LintingType.BPMN_END_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING
                : LintingType.BPMN_INTERMEDIATE_THROW_EVENT_NO_INTERFACE_CLASS_IMPLEMENTING;

        boolean implementsInterface = implementsInterface(implClass, expectedInterface, projectRoot);

        if (!implementsInterface) {
            issues.add(new BpmnElementLintItem(LinterSeverity.ERROR, notImplementingType,
                    elementId, bpmnFile, processId,
                    "Implementation class '" + implClass + "' does not implement "
                            + getSimpleName(expectedInterface) + "."));
        } else {
            issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                    "Implementation class '" + implClass + "' implements " + getSimpleName(expectedInterface) + "."));
        }
    }
}
