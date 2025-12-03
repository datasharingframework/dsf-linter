package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.*;
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
 * </p>
 *
 * <h2>Validation Categories</h2>
 *
 * <h3>Execution Listener Validation</h3>
 * <ul>
 *   <li><strong>Class Existence</strong>: Verifies that execution listener classes exist on the project classpath</li>
 *   <li><strong>Interface Implementation</strong>: Validates that execution listener classes implement
 *       the correct interface based on the API version (ExecutionListener interface for V2 API,
 *       ExecutionListener interface for V1 API)</li>
 * </ul>
 *
 * <h3>Task Listener Validation</h3>
 * <ul>
 *   <li><strong>Class Attribute Presence</strong>: Validates that task listener classes declare a class attribute</li>
 *   <li><strong>Class Existence</strong>: Verifies that task listener classes exist on the project classpath</li>
 *   <li><strong>Inheritance/Interface Check</strong>: Validates that task listener classes extend or implement
 *       the required classes/interfaces based on API version:
 *       <ul>
 *         <li>V1 API: Extend {@code DefaultUserTaskListener} OR implement {@code TaskListener}</li>
 *         <li>V2 API: Extend {@code DefaultUserTaskListener} OR implement {@code UserTaskListener}</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>Task Listener Input Parameters Validation (API V2 only)</h3>
 * <ul>
 *   <li><strong>practitionerRole</strong>: Validates that the practitionerRole input parameter has a non-empty value.
 *       Severity: ERROR if extends DefaultUserTaskListener, WARN otherwise</li>
 *   <li><strong>practitioners</strong>: Validates that the practitioners input parameter has a non-empty value.
 *       Severity: ERROR if extends DefaultUserTaskListener, WARN otherwise</li>
 * </ul>
 *
 * <h3>Task Listener TaskOutput Field Injections Validation (API V2 only)</h3>
 * <p>
 * Validates the taskOutput field injections ({@code taskOutputSystem}, {@code taskOutputCode}, {@code taskOutputVersion})
 * used to configure output parameters for UserTask listeners.
 * </p>
 * <ul>
 *   <li><strong>Completeness Check</strong>: If any of the three fields is set, all three must be set.
 *       Reports: {@link BpmnUserTaskListenerIncompleteTaskOutputFieldsLintItem} (ERROR)</li>
 *   <li><strong>FHIR Resource Validation</strong>:
 *       <ul>
 *         <li><strong>taskOutputSystem</strong>: Should reference a valid CodeSystem URL.
 *             Reports: {@link BpmnUserTaskListenerTaskOutputSystemInvalidFhirResourceLintItem} (ERROR) if CodeSystem is unknown</li>
 *         <li><strong>taskOutputCode</strong>: Should be a valid code in the referenced CodeSystem.
 *             Reports: {@link BpmnUserTaskListenerTaskOutputCodeInvalidFhirResourceLintItem} (ERROR) if code is unknown</li>
 *         <li><strong>taskOutputVersion</strong>: Must contain a placeholder (e.g., {@code #{version}}).
 *             Reports: {@link BpmnUserTaskListenerTaskOutputVersionNoPlaceholderLintItem} (WARN) if no placeholder found</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>Success Reporting</h2>
 * <p>
 * For each successful validation check, a {@link BpmnElementLintItemSuccess} is added to the issues list
 * to provide positive feedback and traceability.
 * </p>
 *
 * @see BpmnUserTaskListenerMissingClassAttributeLintItem
 * @see BpmnUserTaskListenerJavaClassNotFoundLintItem
 * @see BpmnUserTaskListenerNotExtendingOrImplementingRequiredClassLintItem
 * @see BpmnPractitionerRoleHasNoValueOrNullLintItem
 * @see BpmnPractitionersHasNoValueOrNullLintItem
 * @see BpmnUserTaskListenerIncompleteTaskOutputFieldsLintItem
 * @see BpmnUserTaskListenerTaskOutputSystemInvalidFhirResourceLintItem
 * @see BpmnUserTaskListenerTaskOutputCodeInvalidFhirResourceLintItem
 * @see BpmnUserTaskListenerTaskOutputVersionNoPlaceholderLintItem
 * @see BpmnExecutionListenerClassNotFoundLintItem
 * @see BpmnExecutionListenerNotImplementingRequiredInterfaceLintItem
 * @see BpmnElementLintItemSuccess
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
     * <p>
     * Performs comprehensive validation of task listeners on a UserTask, including:
     * </p>
     * <ol>
     *   <li><strong>Class Attribute Presence</strong>: Verifies that the listener declares a class attribute</li>
     *   <li><strong>Class Existence</strong>: Verifies that the listener class exists on the project classpath</li>
     *   <li><strong>Inheritance/Interface Check</strong>: Validates that the listener extends or implements
     *       the required classes/interfaces based on API version</li>
     *   <li><strong>Input Parameters Validation (API V2 only)</strong>: Validates practitionerRole and practitioners
     *       input parameters. Severity: ERROR if extends DefaultUserTaskListener, WARN otherwise</li>
     *   <li><strong>TaskOutput Field Injections Validation (API V2 only)</strong>: Validates taskOutputSystem,
     *       taskOutputCode, and taskOutputVersion field injections for completeness and FHIR resource compliance</li>
     * </ol>
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
                
                // Step 5: For API V2, validate taskOutput field injections
                validateTaskListenerTaskOutputFields(listener, elementId, issues, bpmnFile, processId, projectRoot);
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

    /**
     * Validates taskOutput field injections for task listeners (API v2).
     * <p>
     * Performs validation of the three taskOutput field injections used to configure output parameters
     * for UserTask listeners. The validation includes:
     * </p>
     * <ol>
     *   <li><strong>Completeness Check</strong>: If any of the three fields (taskOutputSystem, taskOutputCode,
     *       taskOutputVersion) is set, all three must be set. Reports:
     *       {@link BpmnUserTaskListenerIncompleteTaskOutputFieldsLintItem} (ERROR) if incomplete</li>
     *   <li><strong>FHIR Resource Validation</strong>:
     *       <ul>
 *         <li><strong>taskOutputSystem</strong>: Should reference a valid CodeSystem URL.
 *             Reports: {@link BpmnUserTaskListenerTaskOutputSystemInvalidFhirResourceLintItem} (ERROR) if CodeSystem is unknown</li>
 *         <li><strong>taskOutputCode</strong>: Should be a valid code in the referenced CodeSystem.
     *             Validated against {@link FhirAuthorizationCache}. Reports:
     *             {@link BpmnUserTaskListenerTaskOutputCodeInvalidFhirResourceLintItem} (ERROR) if code is unknown</li>
     *         <li><strong>taskOutputVersion</strong>: Must contain a placeholder (e.g., {@code #{version}}).
     *             Reports: {@link BpmnUserTaskListenerTaskOutputVersionNoPlaceholderLintItem} (WARN)
     *             if no placeholder found</li>
     *       </ul>
     *   </li>
     * </ol>
     * <p>
     * For each successful validation check, a {@link BpmnElementLintItemSuccess} is added to the issues list.
     * </p>
     *
     * @param listener   the TaskListener to validate
     * @param elementId  the identifier of the BPMN element
     * @param issues     the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile   the BPMN file under lint
     * @param processId  the identifier of the BPMN process containing the element
     * @param projectRoot the project root directory
     */
    private static void validateTaskListenerTaskOutputFields(
            CamundaTaskListener listener,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {

        DomElement domElement = listener.getDomElement();
        if (domElement == null) return;

        // Find extensionElements within the task listener
        DomElement extensionElements = findExtensionElements(domElement);
        if (extensionElements == null) return;

        String taskOutputSystem = null;
        String taskOutputCode = null;
        String taskOutputVersion = null;

        // Read field values from extensionElements
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

        // Check 1: Completeness check - If any of the three fields is set, all three must be set
        boolean anySet = (taskOutputSystem != null && !taskOutputSystem.trim().isEmpty())
                || (taskOutputCode != null && !taskOutputCode.trim().isEmpty())
                || (taskOutputVersion != null && !taskOutputVersion.trim().isEmpty());

        boolean allSet = (taskOutputSystem != null && !taskOutputSystem.trim().isEmpty())
                && (taskOutputCode != null && !taskOutputCode.trim().isEmpty())
                && (taskOutputVersion != null && !taskOutputVersion.trim().isEmpty());

        if (anySet && !allSet) {
            issues.add(new BpmnUserTaskListenerIncompleteTaskOutputFieldsLintItem(
                    elementId, bpmnFile, processId));
            return; // Skip further validation if incomplete
        }

        // If none are set, skip validation
        if (!allSet) {
            return;
        }

        // All three fields are set and not empty (validated in Check 1)
        issues.add(new BpmnElementLintItemSuccess(
                elementId, bpmnFile, processId,
                "All taskOutput fields (taskOutputSystem, taskOutputCode, taskOutputVersion) are set and not blank"));

        // Check 2: FHIR Resource validation
        validateTaskOutputFhirResources(elementId, issues, bpmnFile, processId, projectRoot,
                taskOutputSystem, taskOutputCode, taskOutputVersion);
    }

    /**
     * Finds the extensionElements element within a task listener DOM element.
     *
     * @param taskListenerElement the task listener DOM element
     * @return the extensionElements element, or null if not found
     */
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

    /**
     * Reads the value from a field DOM element.
     * Supports string literals only (expressions are not supported for taskOutput fields).
     *
     * @param fieldElement the field DOM element
     * @return the string value, or null if not found or is an expression
     */
    private static String readFieldValueFromDom(DomElement fieldElement) {
        if (fieldElement == null) return null;

        // Check for stringValue attribute
        String stringValue = fieldElement.getAttribute("stringValue");
        if (stringValue != null && !stringValue.trim().isEmpty()) {
            return stringValue.trim();
        }

        // Check for nested string element
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

    /**
     * Validates that taskOutput field values correspond to valid FHIR resources.
     * <p>
     * Performs FHIR resource validation for the three taskOutput fields:
     * </p>
     * <ul>
     *   <li><strong>taskOutputSystem</strong>: Validates that the system references a valid CodeSystem.
     *       Uses {@link FhirAuthorizationCache#containsSystem(String)} to check if the CodeSystem exists.
     *       Reports: {@link BpmnUserTaskListenerTaskOutputSystemInvalidFhirResourceLintItem} (ERROR) if CodeSystem is unknown</li>
     *   <li><strong>taskOutputCode</strong>: Validates that the code is a valid code in the referenced CodeSystem.
     *       Uses {@link FhirAuthorizationCache#isUnknown(String, String)} to check code existence.
     *       Reports: {@link BpmnUserTaskListenerTaskOutputCodeInvalidFhirResourceLintItem} (ERROR) if code is unknown</li>
     *   <li><strong>taskOutputVersion</strong>: Validates that the version contains a placeholder (e.g., {@code #{version}}).
     *       Uses {@link dev.dsf.linter.util.linting.LintingUtils#containsPlaceholder(String)} to check for placeholders.
     *       Reports: {@link BpmnUserTaskListenerTaskOutputVersionNoPlaceholderLintItem} (WARN) if no placeholder found</li>
     * </ul>
     * <p>
     * For each successful validation check, a {@link BpmnElementLintItemSuccess} is added to the issues list.
     * </p>
     *
     * @param elementId  the identifier of the BPMN element
     * @param issues     the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile   the BPMN file under lint
     * @param processId  the identifier of the BPMN process containing the element
     * @param projectRoot the project root directory
     * @param system     the taskOutputSystem value
     * @param code       the taskOutputCode value
     * @param version    the taskOutputVersion value
     */
    private static void validateTaskOutputFhirResources(
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot,
            String system,
            String code,
            String version) {

        if (system != null && !system.trim().isEmpty()) {
            if (!FhirAuthorizationCache.containsSystem(system)) {
                issues.add(new BpmnUserTaskListenerTaskOutputSystemInvalidFhirResourceLintItem(
                        elementId, bpmnFile, processId, system));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "taskOutputSystem '" + system + "' references a valid CodeSystem"));
            }
        }

        if (system != null && !system.trim().isEmpty() && code != null && !code.trim().isEmpty()) {
            if (FhirAuthorizationCache.isUnknown(system, code)) {
                issues.add(new BpmnUserTaskListenerTaskOutputCodeInvalidFhirResourceLintItem(
                        elementId, bpmnFile, processId, code, system));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "taskOutputCode '" + code + "' is valid in CodeSystem '" + system + "'"));
            }
        }

        if (system != null && !system.trim().isEmpty() && version != null && !version.trim().isEmpty()) {
            if (!containsPlaceholder(version)) {
                issues.add(new BpmnUserTaskListenerTaskOutputVersionNoPlaceholderLintItem(
                        elementId, bpmnFile, processId, version, system));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "taskOutputVersion contains placeholder: '" + version + "'"));
            }
        }
    }
}

