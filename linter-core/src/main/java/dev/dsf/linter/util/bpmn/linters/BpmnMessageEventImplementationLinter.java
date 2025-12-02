package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersion;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.classloading.ClassInspector.*;

/**
 * Utility class for validating implementation classes for message events.
 * <p>
 * This class provides validation methods to ensure that implementation classes for message events
 * (Intermediate Throw Events and End Events) exist and implement the correct interfaces
 * based on the element type and API version.
 * </p>
 */
public final class BpmnMessageEventImplementationLinter {

    private BpmnMessageEventImplementationLinter() {
        // Utility class - no instantiation
    }

    /**
     * Validates implementation class for Message Events (IntermediateThrow, End)
     * with element-specific interface requirements.
     *
     * @param implClass   the implementation class name to validate
     * @param elementId   the identifier of the BPMN element being validated
     * @param elementType the type of BPMN element
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the element
     * @param apiVersion  the API version to use for interface validation
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
            issues.add(new BpmnMessageSendEventImplementationClassNotFoundLintItem(
                    elementId, bpmnFile, processId, implClass));
            return;
        }

        // Step 2: ELEMENT-SPECIFIC interface check
        if (doesNotImplementCorrectInterface(implClass, projectRoot, apiVersion, elementType)) {
            String expectedInterface = getExpectedInterfaceDescription(apiVersion, elementType);

            switch (apiVersion) {
                case V1 -> issues.add(
                        new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateLintItem(
                                elementId, bpmnFile, processId, implClass));
                case V2 -> issues.add(
                        new BpmnEndOrIntermediateThrowEventMissingInterfaceLintItem(
                                elementId, bpmnFile, processId, implClass,
                                "Implementation class '" + implClass
                                        + "' does not implement " + expectedInterface + "."));
            }
            return;
        }

        // Step 3: Success
        String implementedInterface = findImplementedInterface(implClass, projectRoot, apiVersion, elementType);
        String interfaceName = implementedInterface != null
                ? getSimpleName(implementedInterface)
                : getExpectedInterfaceDescription(apiVersion, elementType);

        issues.add(new BpmnElementLintItemSuccess(
                elementId, bpmnFile, processId,
                "Implementation class '" + implClass + "' implements " + interfaceName + "."));
    }
}

