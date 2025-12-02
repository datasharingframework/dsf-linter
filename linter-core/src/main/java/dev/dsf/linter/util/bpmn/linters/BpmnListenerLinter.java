package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static dev.dsf.linter.classloading.ClassInspector.*;
import static dev.dsf.linter.constants.DsfApiConstants.*;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Utility class for validating BPMN execution and task listener classes.
 * <p>
 * This class provides validation methods to ensure that listener classes exist,
 * implement the correct interfaces, and extend the required base classes based on API version.
 * </p>
 */
public final class BpmnListenerLinter {

    private BpmnListenerLinter() {
        // Utility class - no instantiation
    }

    /**
     * Checks execution listener classes on a BPMN element with element-specific interface validation.
     *
     * @param element   the BPMN element to check
     * @param elementId the identifier of the BPMN element
     * @param issues    the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile  the BPMN file under lint
     * @param processId the identifier of the BPMN process containing the element
     * @param projectRoot the project root directory
     */
    public static void checkExecutionListenerClasses(
            BaseElement element,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {
        if (element.getExtensionElements() == null) return;

        Collection<CamundaExecutionListener> listeners =
                element.getExtensionElements().getElementsQuery()
                        .filterByType(CamundaExecutionListener.class)
                        .list();

        // Early return if no execution listeners are defined in the BPMN file
        // (automatically added listeners at runtime are not checked)
        if (listeners == null || listeners.isEmpty()) return;

        ApiVersion apiVersion = ApiVersionHolder.getVersion();

        for (CamundaExecutionListener listener : listeners) {
            String implClass = listener.getCamundaClass();

            // Step 1: Check class existence
            if (isEmpty(implClass)) {
                continue; // Skip empty class names
            }

            if (!classExists(implClass, projectRoot)) {
                issues.add(new BpmnExecutionListenerClassNotFoundLintItem(
                        elementId, bpmnFile, processId, implClass));
                continue;
            }

            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Execution listener class found: " + implClass));

            // Step 2: ELEMENT-SPECIFIC interface check
            if (doesNotImplementCorrectInterface(implClass, projectRoot, apiVersion, BpmnElementType.EXECUTION_LISTENER)) {
                String expectedInterface = getExpectedInterfaceDescription(apiVersion, BpmnElementType.EXECUTION_LISTENER);
                issues.add(new BpmnExecutionListenerNotImplementingRequiredInterfaceLintItem(
                        elementId, bpmnFile, processId, implClass,
                        "Execution listener '" + implClass + "' does not implement " + expectedInterface + "."));
            } else {
                String implementedInterface = findImplementedInterface(implClass, projectRoot, apiVersion, BpmnElementType.EXECUTION_LISTENER);
                String interfaceName = implementedInterface != null
                        ? getSimpleName(implementedInterface)
                        : getExpectedInterfaceDescription(apiVersion, BpmnElementType.EXECUTION_LISTENER);
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "Execution listener '" + implClass + "' implements " + interfaceName + "."));
            }
        }
    }

    /**
     * Checks task listener classes with VERSION-ISOLATED interface validation.
     *
     * @param userTask  the UserTask to check
     * @param elementId the identifier of the BPMN element
     * @param issues    the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile  the BPMN file under lint
     * @param processId the identifier of the BPMN process containing the element
     * @param projectRoot the project root directory
     */
    public static void checkTaskListenerClasses(
            UserTask userTask,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        if (userTask.getExtensionElements() == null) return;

        Collection<CamundaTaskListener> listeners = userTask.getExtensionElements()
                .getElementsQuery()
                .filterByType(CamundaTaskListener.class)
                .list();

        ApiVersion apiVersion = ApiVersionHolder.getVersion();

        for (CamundaTaskListener listener : listeners) {
            String implClass = listener.getCamundaClass();

            // Step 1: Check class attribute presence
            if (isEmpty(implClass)) {
                issues.add(new BpmnUserTaskListenerMissingClassAttributeLintItem(
                        elementId, bpmnFile, processId));
                continue;
            }

            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "UserTask listener declares a class attribute: '" + implClass + "'"));

            // Step 2: Check class existence
            if (!classExists(implClass, projectRoot)) {
                issues.add(new BpmnUserTaskListenerJavaClassNotFoundLintItem(
                        elementId, bpmnFile, processId, implClass));
                continue;
            }

            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "UserTask listener class '" + implClass + "' was found on the project classpath"));

            // Step 3: VERSION-ISOLATED inheritance/interface check
            validateTaskListenerInheritance(
                    implClass, elementId, issues, bpmnFile, processId, projectRoot, apiVersion);
        }
    }

    /**
     * Validates task listener inheritance with version isolation.
     */
    private static void validateTaskListenerInheritance(
            String implClass,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot,
            ApiVersion apiVersion) {

        String defaultSuperClass;
        String requiredInterface;

        switch (apiVersion) {
            case V2 -> {
                defaultSuperClass = V2_DEFAULT_USER_TASK_LISTENER;
                requiredInterface = V2_USER_TASK_LISTENER;
            }
            case V1 -> {
                defaultSuperClass = V1_DEFAULT_USER_TASK_LISTENER;
                requiredInterface = V1_TASK_LISTENER;
            }
            default -> {
                return;
            }
        }

        boolean extendsDefault = isSubclassOf(implClass, defaultSuperClass, projectRoot);
        boolean implementsInterface = implementsInterface(implClass, requiredInterface, projectRoot);

        if (extendsDefault || implementsInterface) {
            String inheritanceDesc = extendsDefault
                    ? "extends " + getSimpleName(defaultSuperClass)
                    : "implements " + getSimpleName(requiredInterface);

            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "UserTask listener '" + implClass + "' " + inheritanceDesc));
        } else {
            issues.add(new BpmnUserTaskListenerNotExtendingOrImplementingRequiredClassLintItem(
                    elementId, bpmnFile, processId, implClass,
                    "UserTask listener '" + implClass + "' does not extend '"
                            + getSimpleName(defaultSuperClass) + "' or implement '"
                            + getSimpleName(requiredInterface) + "'."));
        }
    }
}

