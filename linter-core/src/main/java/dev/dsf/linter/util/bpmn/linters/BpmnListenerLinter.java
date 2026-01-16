package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.BpmnElementLintItem;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.resource.FhirAuthorizationCache;
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
import static dev.dsf.linter.util.linting.LintingUtils.containsPlaceholder;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Utility class for validating BPMN execution and task listener classes.
 * <p>
 * This class provides validation methods to ensure that listener classes exist,
 * implement the correct interfaces, and extend the required base classes based on API version.
 * All validation results are reported as {@link BpmnElementLintItem} instances with different
 * {@link LintingType} values and {@link LinterSeverity} levels.
 * </p>
 *
 * <h2>Validation Categories</h2>
 *
 * <h3>Execution Listener Validation</h3>
 * <ul>
 *   <li><strong>Class Existence</strong>: Verifies that execution listener classes exist on the project classpath.
 *       Reports {@link LintingType#BPMN_EXECUTION_LISTENER_CLASS_NOT_FOUND} with {@link LinterSeverity#ERROR}
 *       if class is not found</li>
 *   <li><strong>Interface Implementation</strong>: Validates that execution listener classes implement
 *       the correct interface based on the API version ({@code ExecutionListener} interface for both V1 and V2 API).
 *       Reports {@link LintingType#BPMN_EXECUTION_LISTENER_NOT_IMPLEMENTING_REQUIRED_INTERFACE} with
 *       {@link LinterSeverity#ERROR} if interface is not implemented</li>
 * </ul>
 *
 * <h3>Task Listener Validation</h3>
 * <ul>
 *   <li><strong>Class Attribute Presence</strong>: Validates that task listener classes declare a class attribute.
 *       Reports {@link LintingType#BPMN_USER_TASK_LISTENER_MISSING_CLASS_ATTRIBUTE} with {@link LinterSeverity#ERROR}
 *       if class attribute is missing</li>
 *   <li><strong>Class Existence</strong>: Verifies that task listener classes exist on the project classpath.
 *       Reports {@link LintingType#BPMN_USER_TASK_LISTENER_JAVA_CLASS_NOT_FOUND} with {@link LinterSeverity#ERROR}
 *       if class is not found</li>
 *   <li><strong>Inheritance/Interface Check</strong>: Validates that task listener classes extend or implement
 *       the required classes/interfaces based on API version:
 *       <ul>
 *         <li>V1 API: Must extend {@code DefaultUserTaskListener} OR implement {@code TaskListener}</li>
 *         <li>V2 API: Must extend {@code DefaultUserTaskListener} OR implement {@code UserTaskListener}</li>
 *       </ul>
 *       Reports {@link LintingType#BPMN_USER_TASK_LISTENER_NOT_EXTENDING_OR_IMPLEMENTING_REQUIRED_CLASS} with
 *       {@link LinterSeverity#ERROR} if neither condition is met
 *   </li>
 * </ul>
 *
 * <h3>Task Listener Input Parameters Validation (API V2 only)</h3>
 * <p>
 * For UserTask listeners in API V2, validates required input parameters:
 * </p>
 * <ul>
 *   <li><strong>practitionerRole</strong>: Validates that the practitionerRole input parameter has a non-empty value.
 *       Reports {@link LintingType#BPMN_PRACTITIONER_ROLE_HAS_NO_VALUE_OR_NULL} with {@link LinterSeverity#ERROR}
 *       if extends {@code DefaultUserTaskListener}, {@link LinterSeverity#WARN} otherwise</li>
 *   <li><strong>practitioners</strong>: Validates that the practitioners input parameter has a non-empty value.
 *       Reports {@link LintingType#BPMN_PRACTITIONERS_HAS_NO_VALUE_OR_NULL} with {@link LinterSeverity#ERROR}
 *       if extends {@code DefaultUserTaskListener}, {@link LinterSeverity#WARN} otherwise</li>
 * </ul>
 *
 * <h3>Task Listener TaskOutput Field Injections Validation (API V2 only)</h3>
 * <p>
 * Validates the taskOutput field injections ({@code taskOutputSystem}, {@code taskOutputCode}, {@code taskOutputVersion})
 * used to configure output parameters for UserTask listeners.
 * </p>
 * <ul>
 *   <li><strong>Completeness Check</strong>: If any of the three fields is set, all three must be set.
 *       Reports {@link LintingType#BPMN_USER_TASK_LISTENER_INCOMPLETE_TASK_OUTPUT_FIELDS} with {@link LinterSeverity#ERROR}
 *       if incomplete</li>
 *   <li><strong>FHIR Resource Validation</strong>:
 *       <ul>
 *         <li><strong>taskOutputSystem</strong>: Should reference a valid CodeSystem URL via {@link FhirAuthorizationCache}.
 *             Reports {@link LintingType#BPMN_USER_TASK_LISTENER_TASK_OUTPUT_SYSTEM_INVALID_FHIR_RESOURCE} with
 *             {@link LinterSeverity#ERROR} if CodeSystem is unknown</li>
 *         <li><strong>taskOutputCode</strong>: Should be a valid code in the referenced CodeSystem.
 *             Reports {@link LintingType#BPMN_USER_TASK_LISTENER_TASK_OUTPUT_CODE_INVALID_FHIR_RESOURCE} with
 *             {@link LinterSeverity#ERROR} if code is unknown</li>
 *         <li><strong>taskOutputVersion</strong>: Must contain a placeholder (e.g., {@code #{version}}).
 *             Reports {@link LintingType#BPMN_USER_TASK_LISTENER_TASK_OUTPUT_VERSION_NO_PLACEHOLDER} with
 *             {@link LinterSeverity#WARN} if no placeholder found</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>Success Reporting</h2>
 * <p>
 * For each successful validation check, a success {@link BpmnElementLintItem} (created via
 * {@link BpmnElementLintItem#success(String, File, String, String)}) is added to the issues list
 * to provide positive feedback and traceability.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This is a utility class with only static methods and no mutable state, making it thread-safe.
 * All methods can be called concurrently from multiple threads.
 * </p>
 *
 * @see BpmnElementLintItem
 * @see LintingType
 * @see LinterSeverity
 * @see FhirAuthorizationCache
 * @see ApiVersion
 * @see ApiVersionHolder
 */
public final class BpmnListenerLinter {

    private BpmnListenerLinter() {
    }

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

        if (listeners == null || listeners.isEmpty()) return;

        ApiVersion apiVersion = ApiVersionHolder.getVersion();

        for (CamundaExecutionListener listener : listeners) {
            String implClass = listener.getCamundaClass();

            if (isEmpty(implClass)) {
                continue;
            }

            if (!classExists(implClass, projectRoot)) {
                issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                        LintingType.BPMN_EXECUTION_LISTENER_CLASS_NOT_FOUND,
                        elementId, bpmnFile, processId,
                        "Execution listener class '" + implClass + "' not found."));
                continue;
            }

            issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                    "Execution listener class found: " + implClass));

            if (doesNotImplementCorrectInterface(implClass, projectRoot, apiVersion, BpmnElementType.EXECUTION_LISTENER)) {
                String expectedInterface = getExpectedInterfaceDescription(apiVersion, BpmnElementType.EXECUTION_LISTENER);
                issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                        LintingType.BPMN_EXECUTION_LISTENER_NOT_IMPLEMENTING_REQUIRED_INTERFACE,
                        elementId, bpmnFile, processId,
                        "Execution listener '" + implClass + "' does not implement " + expectedInterface + "."));
            } else {
                String implementedInterface = findImplementedInterface(implClass, projectRoot, apiVersion, BpmnElementType.EXECUTION_LISTENER);
                String interfaceName = implementedInterface != null
                        ? getSimpleName(implementedInterface)
                        : getExpectedInterfaceDescription(apiVersion, BpmnElementType.EXECUTION_LISTENER);
                issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                        "Execution listener '" + implClass + "' implements " + interfaceName + "."));
            }
        }
    }

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

            if (isEmpty(implClass)) {
                issues.add(BpmnElementLintItem.of(LinterSeverity.ERROR,
                        LintingType.BPMN_USER_TASK_LISTENER_MISSING_CLASS_ATTRIBUTE,
                        elementId, bpmnFile, processId));
                continue;
            }

            issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                    "UserTask listener declares a class attribute: '" + implClass + "'"));

            if (!classExists(implClass, projectRoot)) {
                issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                        LintingType.BPMN_USER_TASK_LISTENER_JAVA_CLASS_NOT_FOUND,
                        elementId, bpmnFile, processId,
                        "UserTask listener class '" + implClass + "' not found."));
                continue;
            }

            issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                    "UserTask listener class '" + implClass + "' was found on the project classpath"));

            validateTaskListenerInheritance(implClass, elementId, issues, bpmnFile, processId, projectRoot, apiVersion);

            if (apiVersion == ApiVersion.V2) {
                boolean extendsDefaultUserTaskListener = isSubclassOf(implClass, V2_DEFAULT_USER_TASK_LISTENER, projectRoot);
                validateTaskListenerInputParameters(listener, elementId, issues, bpmnFile, processId, extendsDefaultUserTaskListener);
                validateTaskListenerTaskOutputFields(listener, elementId, issues, bpmnFile, processId);
            }
        }
    }

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

            issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                    "UserTask listener '" + implClass + "' " + inheritanceDesc));
        } else {
            issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                    LintingType.BPMN_USER_TASK_LISTENER_NOT_EXTENDING_OR_IMPLEMENTING_REQUIRED_CLASS,
                    elementId, bpmnFile, processId,
                    "UserTask listener '" + implClass + "' does not extend '"
                            + getSimpleName(defaultSuperClass) + "' or implement '"
                            + getSimpleName(requiredInterface) + "'."));
        }
    }

    private static void validateTaskListenerInputParameters(
            CamundaTaskListener listener,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            boolean extendsDefaultUserTaskListener) {

        DomElement domElement = listener.getDomElement();
        if (domElement == null) return;

        DomElement inputOutput = findInputOutput(domElement);
        if (inputOutput == null) return;

        LinterSeverity severity = extendsDefaultUserTaskListener ? LinterSeverity.ERROR : LinterSeverity.WARN;

        validateInputParameter(inputOutput, "practitionerRole", elementId, issues, bpmnFile, processId, severity);
        validateInputParameter(inputOutput, "practitioners", elementId, issues, bpmnFile, processId, severity);
    }

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

    private static void validateInputParameter(
            DomElement inputOutput,
            String paramName,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            LinterSeverity severity) {

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

        if (inputParam == null) return;

        String value = readInputParameterValue(inputParam);

        if (value == null || value.trim().isEmpty()) {
            LintingType type = "practitionerRole".equals(paramName)
                    ? LintingType.BPMN_PRACTITIONER_ROLE_HAS_NO_VALUE_OR_NULL
                    : LintingType.BPMN_PRACTITIONERS_HAS_NO_VALUE_OR_NULL;
            issues.add(BpmnElementLintItem.of(severity, type, elementId, bpmnFile, processId));
        } else {
            issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                    "Task listener input parameter '" + paramName + "' has a non-empty value"));
        }
    }

    private static String readInputParameterValue(DomElement inputParam) {
        String textContent = inputParam.getTextContent();
        if (textContent != null && !textContent.trim().isEmpty()) {
            return textContent.trim();
        }

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
                    for (DomElement listChild : child.getChildElements()) {
                        if (listChild.getNamespaceURI() != null
                                && listChild.getNamespaceURI().equals("http://camunda.org/schema/1.0/bpmn")
                                && "value".equals(listChild.getLocalName())) {
                            String listValue = listChild.getTextContent();
                            if (listValue != null && !listValue.trim().isEmpty()) {
                                return listValue.trim();
                            }
                        }
                    }
                    return "";
                }
            }
        }

        return null;
    }

    private static void validateTaskListenerTaskOutputFields(
            CamundaTaskListener listener,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        DomElement domElement = listener.getDomElement();
        if (domElement == null) return;

        DomElement extensionElements = findExtensionElements(domElement);
        if (extensionElements == null) return;

        String taskOutputSystem = null;
        String taskOutputCode = null;
        String taskOutputVersion = null;

        for (DomElement child : extensionElements.getChildElements()) {
            String namespaceUri = child.getNamespaceURI();
            String localName = child.getLocalName();
            if (namespaceUri != null && namespaceUri.equals("http://camunda.org/schema/1.0/bpmn")
                    && "field".equals(localName)) {
                String fieldName = child.getAttribute("name");
                if (fieldName == null) continue;

                String value = readFieldValueFromDom(child);
                if (value == null) continue;

                switch (fieldName) {
                    case "taskOutputSystem" -> taskOutputSystem = value;
                    case "taskOutputCode" -> taskOutputCode = value;
                    case "taskOutputVersion" -> taskOutputVersion = value;
                }
            }
        }

        boolean anySet = (taskOutputSystem != null && !taskOutputSystem.trim().isEmpty())
                || (taskOutputCode != null && !taskOutputCode.trim().isEmpty())
                || (taskOutputVersion != null && !taskOutputVersion.trim().isEmpty());

        boolean allSet = (taskOutputSystem != null && !taskOutputSystem.trim().isEmpty())
                && (taskOutputCode != null && !taskOutputCode.trim().isEmpty())
                && (taskOutputVersion != null && !taskOutputVersion.trim().isEmpty());

        if (anySet && !allSet) {
            issues.add(BpmnElementLintItem.of(LinterSeverity.ERROR,
                    LintingType.BPMN_USER_TASK_LISTENER_INCOMPLETE_TASK_OUTPUT_FIELDS,
                    elementId, bpmnFile, processId));
            return;
        }

        if (!allSet) {
            return;
        }

        issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                "All taskOutput fields (taskOutputSystem, taskOutputCode, taskOutputVersion) are set and not blank"));

        validateTaskOutputFhirResources(elementId, issues, bpmnFile, processId,
                taskOutputSystem, taskOutputCode, taskOutputVersion);
    }

    private static DomElement findExtensionElements(DomElement taskListenerElement) {
        for (DomElement child : taskListenerElement.getChildElements()) {
            String namespaceUri = child.getNamespaceURI();
            String localName = child.getLocalName();
            if (namespaceUri != null && namespaceUri.equals("http://camunda.org/schema/1.0/bpmn")
                    && "extensionElements".equals(localName)) {
                return child;
            }
        }
        return null;
    }

    private static String readFieldValueFromDom(DomElement fieldElement) {
        if (fieldElement == null) return null;

        String stringValue = fieldElement.getAttribute("stringValue");
        if (stringValue != null && !stringValue.trim().isEmpty()) {
            return stringValue.trim();
        }

        for (DomElement child : fieldElement.getChildElements()) {
            String namespaceUri = child.getNamespaceURI();
            String localName = child.getLocalName();
            if (namespaceUri != null && namespaceUri.equals("http://camunda.org/schema/1.0/bpmn")
                    && "string".equals(localName)) {
                String value = child.getTextContent();
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }

        return null;
    }

    private static void validateTaskOutputFhirResources(
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            String system,
            String code,
            String version) {

        if (system != null && !system.trim().isEmpty()) {
            if (!FhirAuthorizationCache.containsSystem(system)) {
                issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                        LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_SYSTEM_INVALID_FHIR_RESOURCE,
                        elementId, bpmnFile, processId,
                        "taskOutputSystem '" + system + "' references unknown CodeSystem."));
            } else {
                issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                        "taskOutputSystem '" + system + "' references a valid CodeSystem"));
            }
        }

        if (system != null && !system.trim().isEmpty() && code != null && !code.trim().isEmpty()) {
            if (FhirAuthorizationCache.isUnknown(system, code)) {
                issues.add(new BpmnElementLintItem(LinterSeverity.ERROR,
                        LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_CODE_INVALID_FHIR_RESOURCE,
                        elementId, bpmnFile, processId,
                        "taskOutputCode '" + code + "' is unknown in CodeSystem '" + system + "'."));
            } else {
                issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                        "taskOutputCode '" + code + "' is valid in CodeSystem '" + system + "'"));
            }
        }

        if (system != null && !system.trim().isEmpty() && version != null && !version.trim().isEmpty()) {
            if (!containsPlaceholder(version)) {
                issues.add(new BpmnElementLintItem(LinterSeverity.WARN,
                        LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_VERSION_NO_PLACEHOLDER,
                        elementId, bpmnFile, processId,
                        "taskOutputVersion '" + version + "' does not contain a placeholder."));
            } else {
                issues.add(BpmnElementLintItem.success(elementId, bpmnFile, processId,
                        "taskOutputVersion contains placeholder: '" + version + "'"));
            }
        }
    }
}
