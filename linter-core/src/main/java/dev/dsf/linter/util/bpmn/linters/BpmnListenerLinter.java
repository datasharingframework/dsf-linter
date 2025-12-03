package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;
import org.camunda.bpm.model.xml.instance.DomElement;

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

            // Step 4: For API V2, validate input parameters for all task listeners
            // Severity: ERROR if extends DefaultUserTaskListener, WARN otherwise
            if (apiVersion == ApiVersion.V2) {
                boolean extendsDefaultUserTaskListener = isSubclassOf(implClass, V2_DEFAULT_USER_TASK_LISTENER, projectRoot);
                validateTaskListenerInputParameters(listener, implClass, elementId, issues, bpmnFile, processId, extendsDefaultUserTaskListener);
            }
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

    /**
     * Validates input parameters for task listeners (API v2).
     * Checks that practitionerRole and practitioners input parameters have non-empty values.
     * Severity: ERROR if extends DefaultUserTaskListener, WARN otherwise.
     *
     * @param listener                      the TaskListener to validate
     * @param implClass                     the implementation class name
     * @param elementId                     the identifier of the BPMN element
     * @param issues                        the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile                      the BPMN file under lint
     * @param processId                     the identifier of the BPMN process containing the element
     * @param extendsDefaultUserTaskListener true if the listener extends DefaultUserTaskListener, false otherwise
     */
    private static void validateTaskListenerInputParameters(
            CamundaTaskListener listener,
            String implClass,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            boolean extendsDefaultUserTaskListener) {

        DomElement domElement = listener.getDomElement();
        if (domElement == null) return;

        // Check for inputOutput element within the task listener
        DomElement inputOutput = findInputOutput(domElement);
        if (inputOutput == null) return;

        // Determine severity: ERROR for DefaultUserTaskListener, WARN for others
        LinterSeverity severity = extendsDefaultUserTaskListener ? LinterSeverity.ERROR : LinterSeverity.WARN;

        // Check for practitionerRole input parameter
        validateInputParameter(inputOutput, "practitionerRole", elementId, issues, bpmnFile, processId, severity);

        // Check for practitioners input parameter
        validateInputParameter(inputOutput, "practitioners", elementId, issues, bpmnFile, processId, severity);
    }

    /**
     * Finds the inputOutput element within a task listener DOM element.
     *
     * @param taskListenerElement the task listener DOM element
     * @return the inputOutput element, or null if not found
     */
    private static DomElement findInputOutput(DomElement taskListenerElement) {
        for (DomElement child : taskListenerElement.getChildElements()) {
            String namespaceUri = child.getNamespaceURI();
            String localName = child.getLocalName();
            if (namespaceUri != null && namespaceUri.equals("http://camunda.org/schema/1.0/bpmn") 
                    && "inputOutput".equals(localName)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Validates a specific input parameter to ensure it has a non-empty value.
     *
     * @param inputOutput the inputOutput DOM element
     * @param paramName    the name of the input parameter to validate
     * @param elementId    the identifier of the BPMN element
     * @param issues       the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile     the BPMN file under lint
     * @param processId    the identifier of the BPMN process containing the element
     * @param severity     the severity level (ERROR for DefaultUserTaskListener, WARN for others)
     */
    private static void validateInputParameter(
            DomElement inputOutput,
            String paramName,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            LinterSeverity severity) {

        // Find input parameter with the given name
        DomElement inputParam = null;
        for (DomElement child : inputOutput.getChildElements()) {
            String namespaceUri = child.getNamespaceURI();
            String localName = child.getLocalName();
            if (namespaceUri != null && namespaceUri.equals("http://camunda.org/schema/1.0/bpmn")
                    && "inputParameter".equals(localName)) {
                String nameAttr = child.getAttribute("name");
                if (paramName.equals(nameAttr)) {
                    inputParam = child;
                    break;
                }
            }
        }

        // If input parameter is not found, skip validation
        if (inputParam == null) return;

        // Read the value from the input parameter
        String value = readInputParameterValue(inputParam);

        // Validate that value is not null or empty
        if (value == null || value.trim().isEmpty()) {
            if ("practitionerRole".equals(paramName)) {
                issues.add(new BpmnPractitionerRoleHasNoValueOrNullLintItem(
                        severity, elementId, bpmnFile, processId));
            } else if ("practitioners".equals(paramName)) {
                issues.add(new BpmnPractitionersHasNoValueOrNullLintItem(
                        severity, elementId, bpmnFile, processId));
            }
        } else {
            // Success: parameter has a non-empty value
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Task listener input parameter '" + paramName + "' has a non-empty value"));
        }
    }

    /**
     * Reads the value from an input parameter DOM element.
     * Supports various value formats (string, list, etc.).
     *
     * @param inputParam the input parameter DOM element
     * @return the string representation of the value, or null if no value found
     */
    private static String readInputParameterValue(DomElement inputParam) {
        // Check if there's a text content directly
        String textContent = inputParam.getTextContent();
        if (textContent != null && !textContent.trim().isEmpty()) {
            return textContent.trim();
        }

        // Check for nested elements (string, list, etc.)
        for (DomElement child : inputParam.getChildElements()) {
            String namespaceUri = child.getNamespaceURI();
            String localName = child.getLocalName();
            if (namespaceUri != null && namespaceUri.equals("http://camunda.org/schema/1.0/bpmn")) {
                if ("string".equals(localName)) {
                    String value = child.getTextContent();
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                } else if ("list".equals(localName)) {
                    // For list, check if there are any value elements
                    for (DomElement listChild : child.getChildElements()) {
                        if (listChild.getNamespaceURI() != null
                                && listChild.getNamespaceURI().equals("http://camunda.org/schema/1.0/bpmn")
                                && "value".equals(listChild.getLocalName())) {
                            String listValue = listChild.getTextContent();
                            if (listValue != null && !listValue.trim().isEmpty()) {
                                return listValue.trim(); // Return first non-empty value
                            }
                        }
                    }
                    // If list exists but has no values, return empty string to trigger validation
                    return "";
                }
            }
        }

        return null;
    }
}

