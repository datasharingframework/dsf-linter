package dev.dsf.linter.bpmn;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.resource.FhirResourceLocator;
import org.camunda.bpm.model.bpmn.instance.ReceiveTask;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.UserTask;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkExecutionListenerClasses;
import static dev.dsf.linter.bpmn.BpmnElementLinter.checkMessageName;
import static dev.dsf.linter.bpmn.BpmnElementLinter.checkTaskListenerClasses;
import static dev.dsf.linter.classloading.ClassInspector.*;
import static dev.dsf.linter.constants.DsfApiConstants.*;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Specialized linter class for validating BPMN task elements against business logic and FHIR-related constraints.
 * 
 * <p>
 * The {@code BpmnTaskLinter} serves as a specialized component for performing comprehensive validation
 * of BPMN 2.0 task elements used in Camunda workflows. It validates Service Tasks, User Tasks,
 * Send Tasks, and Receive Tasks to ensure compliance with naming conventions, class implementation
 * requirements, FHIR resource referencing rules, and task-specific configuration requirements.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * This class is instantiated by {@link BpmnModelLinter} and provides specialized validation for
 * task elements. It delegates common validation tasks to utility methods in {@link BpmnElementLinter}
 * and {@link BpmnFieldInjectionLinter}, while handling task-specific validation logic internally.
 * The class uses element-specific interface validation to ensure that each task type implements
 * the correct interface for its specific element type, not just any DSF interface.
 * </p>
 *
 * <h2>Validation Categories</h2>
 * <p>
 * The linter performs the following categories of checks:
 * </p>
 *
 * <h3>Service Task Validation</h3>
 * <ul>
 *   <li><strong>Task Name Validation</strong>: Validates that service tasks have non-empty names</li>
 *   <li><strong>Implementation Class Validation</strong>: Validates that implementation classes exist
 *       on the project classpath and implement the correct interface or extend the correct base class
 *       based on the API version</li>
 *   <li><strong>V1 API Compliance</strong>: For V1 API, ensures that service task implementation
 *       classes either extend {@code AbstractServiceDelegate} or implement {@code JavaDelegate} interface</li>
 *   <li><strong>V2 API Compliance</strong>: For V2 API, ensures that service task implementation
 *       classes implement the {@code ServiceTask} interface</li>
 * </ul>
 *
 * <h3>User Task Validation</h3>
 * <ul>
 *   <li><strong>Task Name Validation</strong>: Validates that user tasks have non-empty names</li>
 *   <li><strong>Form Key Validation</strong>: Validates that formKey attributes are non-empty and
 *       reference external forms (starting with "external:", "http://", or "https://")</li>
 *   <li><strong>Questionnaire Validation</strong>: Verifies that external form keys correspond to
 *       existing FHIR Questionnaire resources in the project</li>
 *   <li><strong>Task Listener Validation</strong>: Validates that task listener classes exist, declare
 *       class attributes, and extend or implement the required classes/interfaces based on the API version</li>
 * </ul>
 *
 * <h3>Send Task Validation</h3>
 * <ul>
 *   <li><strong>Task Name Validation</strong>: Validates that send tasks have non-empty names</li>
 *   <li><strong>Implementation Class Validation</strong>: Validates that implementation classes exist
 *       on the project classpath and implement the correct interface or extend the correct base class
 *       based on the API version</li>
 *   <li><strong>V1 API Compliance</strong>: For V1 API, ensures that send task implementation
 *       classes either extend {@code AbstractTaskMessageSend} or implement {@code JavaDelegate} interface</li>
 *   <li><strong>V2 API Compliance</strong>: For V2 API, ensures that send task implementation
 *       classes implement the {@code MessageSendTask} interface</li>
 *   <li><strong>Field Injection Validation</strong>: Validates Camunda field injection values for
 *       {@code profile}, {@code messageName}, and {@code instantiatesCanonical} fields</li>
 * </ul>
 *
 * <h3>Receive Task Validation</h3>
 * <ul>
 *   <li><strong>Task Name Validation</strong>: Validates that receive tasks have non-empty names</li>
 *   <li><strong>Message Definition Validation</strong>: Validates that message definitions are present
 *       and have non-empty message names</li>
 *   <li><strong>FHIR Resource Validation</strong>: Verifies that message names correspond to existing
 *       FHIR ActivityDefinition and StructureDefinition resources in the project</li>
 * </ul>
 *
 * <h3>Implementation Class Validation</h3>
 * <p>
 * For service tasks and send tasks, the linter validates:
 * </p>
 * <ul>
 *   <li><strong>Class Existence</strong>: Verifies that implementation classes exist on the project classpath</li>
 *   <li><strong>V1 API Inheritance/Interface Compliance</strong>: For V1 API, validates that implementation classes
 *       either extend the appropriate abstract base class or implement the required interface:
 *       <ul>
 *         <li>Service Tasks: Extend {@code AbstractServiceDelegate} OR implement {@code JavaDelegate}</li>
 *         <li>Send Tasks: Extend {@code AbstractTaskMessageSend} OR implement {@code JavaDelegate}</li>
 *       </ul>
 *   </li>
 *   <li><strong>V2 API Interface Compliance</strong>: For V2 API, validates that implementation classes
 *       implement the correct interface:
 *       <ul>
 *         <li>ServiceTask interface for Service Tasks</li>
 *         <li>MessageSendTask interface for Send Tasks</li>
 *       </ul>
 *   </li>
 *   <li><strong>API Version Isolation</strong>: Uses version-specific inheritance/interface requirements to ensure
 *       compatibility with the correct DSF BPE API version</li>
 * </ul>
 *
 * <h3>Execution Listener Validation</h3>
 * <p>
 * For all task types (Service Tasks, Send Tasks, User Tasks, and Receive Tasks), the linter validates:
 * </p>
 * <ul>
 *   <li><strong>Class Existence</strong>: Verifies that execution listener classes exist on the project classpath</li>
 *   <li><strong>Interface Implementation</strong>: Validates that execution listener classes implement
 *       the correct interface based on the API version (ExecutionListener interface for V2 API,
 *       ExecutionListener interface for V1 API)</li>
 *   <li><strong>API Version Isolation</strong>: Uses version-specific interface requirements to ensure
 *       compatibility with the correct DSF BPE API version</li>
 * </ul>
 *
 * <h3>FHIR-Specific Validation</h3>
 * <p>
 * For user tasks and receive tasks, the following FHIR-related validations are performed:
 * </p>
 * <ul>
 *   <li><strong>Questionnaire Validation</strong>: For user tasks, verifies that external form keys
 *       correspond to existing FHIR Questionnaire resources</li>
 *   <li><strong>ActivityDefinition Validation</strong>: For receive tasks, verifies that message names
 *       correspond to existing FHIR ActivityDefinition resources</li>
 *   <li><strong>StructureDefinition Validation</strong>: For receive tasks, verifies that message names
 *       correspond to existing FHIR StructureDefinition resources</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * File projectRoot = new File("/path/to/project");
 * BpmnTaskLinter linter = new BpmnTaskLinter(projectRoot);
 * 
 * List<BpmnElementLintItem> issues = new ArrayList<>();
 * ServiceTask serviceTask = ...; // obtained from BPMN model
 * File bpmnFile = new File("process.bpmn");
 * String processId = "myProcess";
 * 
 * linter.lintServiceTask(serviceTask, issues, bpmnFile, processId);
 * 
 * for (BpmnElementLintItem issue : issues) {
 *     System.out.println(issue.getSeverity() + ": " + issue.getMessage());
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is not thread-safe. Each linting operation uses the instance's {@code projectRoot} field,
 * which requires external synchronization if the same instance is used across multiple threads.
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">Camunda Extension Elements</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/questionnaire.html">FHIR Questionnaire</a></li>
 * </ul>
 *
 * @see BpmnModelLinter
 * @see BpmnElementLinter
 * @see BpmnFieldInjectionLinter
 * @see BpmnUserTaskNameEmptyLintItem
 * @see BpmnUserTaskFormKeyEmptyLintItem
 * @see BpmnUserTaskFormKeyIsNotAnExternalFormLintItem
 * @see BpmnUserTaskListenerMissingClassAttributeLintItem
 * @see BpmnUserTaskListenerJavaClassNotFoundLintItem
 * @see BpmnUserTaskListenerNotExtendingOrImplementingRequiredClassLintItem
 * @see BpmnExecutionListenerClassNotFoundLintItem
 * @see BpmnExecutionListenerNotImplementingRequiredInterfaceLintItem
 * @since 1.0
 */
public class BpmnTaskLinter
{
    private final File projectRoot;

    /**
     * Constructs a new {@code BpmnTaskLinter} instance with the specified project root directory.
     * 
     * <p>
     * The project root is used by the linter to locate compiled classes, FHIR resources, and other
     * project artifacts required for validation. It typically points to the root directory of a Maven
     * or Gradle project containing build output directories such as {@code target/classes} or
     * {@code build/classes}.
     * </p>
     *
     * @param projectRoot the root directory of the project; must not be {@code null}
     * @throws IllegalArgumentException if {@code projectRoot} is {@code null}
     */
    public BpmnTaskLinter(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    // ==================== SERVICE TASK ====================

    /**
     * Lints a {@link ServiceTask} with element-specific interface validation.
     * <p>
     * Validates the task name, implementation class existence and interface compliance,
     * and execution listener classes if present.
     *
     * @param task      the ServiceTask to lint
     * @param issues    the list to collect lint items
     * @param bpmnFile  the BPMN file reference
     * @param processId the process identifier
     */
    public void lintServiceTask(
            ServiceTask task,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = task.getId();
        ApiVersion apiVersion = ApiVersionHolder.getVersion();

        // 1. Validate task name
        if (isEmpty(task.getName())) {
            issues.add(new BpmnServiceTaskNameEmptyLintItem(elementId, bpmnFile, processId));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "ServiceTask has a non-empty name: '" + task.getName() + "'"));
        }

        // 2. Validate implementation class
        String implClass = task.getCamundaClass();

        if (implClass == null) {
            issues.add(new BpmnServiceTaskImplementationNotExistLintItem(elementId, bpmnFile, processId));
            return;
        }

        if (implClass.trim().isEmpty()) {
            issues.add(new BpmnServiceTaskImplementationClassEmptyLintItem(elementId, bpmnFile, processId));
            return;
        }

        if (!classExists(implClass, projectRoot)) {
            issues.add(new BpmnServiceTaskImplementationClassNotFoundLintItem(
                    elementId, bpmnFile, processId, implClass));
            return;
        }

        // Element-specific interface/inheritance check
        if (apiVersion == ApiVersion.V1) {
            // V1: Check inheritance and interface separately
            boolean extendsAbstract = isSubclassOf(implClass, V1_ABSTRACT_SERVICE_DELEGATE, projectRoot);
            boolean implementsInterface = implementsInterface(implClass, V1_JAVA_DELEGATE, projectRoot);

            // Check 1: Extends AbstractServiceDelegate
            if (extendsAbstract) {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "ServiceTask implementation class '" + implClass + "' extends " + getSimpleName(V1_ABSTRACT_SERVICE_DELEGATE) + "."));
            } else {
                issues.add(new BpmnServiceTaskNotExtendingAbstractServiceDelegateLintItem(
                        elementId, bpmnFile, processId, implClass,
                        "ServiceTask implementation class '" + implClass + "' does not extend '" + getSimpleName(V1_ABSTRACT_SERVICE_DELEGATE) + "'."));
            }

            // Check 2: Implements JavaDelegate
            if (implementsInterface) {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "ServiceTask implementation class '" + implClass + "' implements " + getSimpleName(V1_JAVA_DELEGATE) + "."));
            } else {
                issues.add(new BpmnServiceTaskImplementationClassNotImplementingJavaDelegateLintItem(
                        elementId, bpmnFile, processId, implClass,
                        "ServiceTask implementation class '" + implClass + "' does not implement '" + getSimpleName(V1_JAVA_DELEGATE) + "'."));
            }
        } else {
            // V2: Check interface implementation
            if (doesNotImplementCorrectInterface(implClass, projectRoot, apiVersion, BpmnElementType.SERVICE_TASK)) {
                issues.add(new BpmnServiceTaskNoInterfaceClassImplementingLintItem(
                        elementId, bpmnFile, processId,
                        "ServiceTask implementation class '" + implClass
                                + "' does not implement ServiceTask interface."));
                return;
            }

            // Success
            String implementedInterface = findImplementedInterface(implClass, projectRoot, apiVersion, BpmnElementType.SERVICE_TASK);
            String interfaceName = implementedInterface != null
                    ? getSimpleName(implementedInterface)
                    : "ServiceTask";

            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "ServiceTask implementation class '" + implClass + "' implements " + interfaceName + "."));
        }

        // Check execution listener classes (ServiceTasks can also have execution listeners)
        checkExecutionListenerClasses(task, elementId, issues, bpmnFile, processId, projectRoot);
    }

    // ==================== SEND TASK ====================

    /**
     * Lints a {@link SendTask} with element-specific interface validation.
     * <p>
     * Validates the task name, implementation class existence and interface compliance,
     * field injections, and execution listener classes if present.
     *
     * @param sendTask  the SendTask to lint
     * @param issues    the list to collect lint items
     * @param bpmnFile  the BPMN file reference
     * @param processId the process identifier
     */
    public void lintSendTask(
            SendTask sendTask,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = sendTask.getId();
        ApiVersion apiVersion = ApiVersionHolder.getVersion();

        // 1. Validate task name
        if (isEmpty(sendTask.getName())) {
            issues.add(new BpmnEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "SendTask has a non-empty name: '" + sendTask.getName() + "'"));
        }

        // 2. Validate implementation class
        String implClass = sendTask.getCamundaClass();

        if (isEmpty(implClass)) {
            issues.add(new BpmnMessageSendTaskImplementationClassEmptyLintItem(elementId, bpmnFile, processId));
        } else if (!classExists(implClass, projectRoot)) {
            issues.add(new BpmnMessageSendTaskImplementationClassNotFoundLintItem(
                    elementId, bpmnFile, processId, implClass));
        } else {
            // Element-specific interface/inheritance check
            if (apiVersion == ApiVersion.V1) {
                // V1: Check inheritance and interface separately
                boolean extendsAbstract = isSubclassOf(implClass, V1_ABSTRACT_TASK_MESSAGE_SEND, projectRoot);
                boolean implementsInterface = implementsInterface(implClass, V1_JAVA_DELEGATE, projectRoot);

                // Check 1: Extends AbstractTaskMessageSend
                if (extendsAbstract) {
                    issues.add(new BpmnElementLintItemSuccess(
                            elementId, bpmnFile, processId,
                            "SendTask implementation class '" + implClass + "' extends " + getSimpleName(V1_ABSTRACT_TASK_MESSAGE_SEND) + "."));
                } else {
                    issues.add(new BpmnSendTaskNotExtendingAbstractTaskMessageSendLintItem(
                            elementId, bpmnFile, processId, implClass,
                            "SendTask implementation class '" + implClass + "' does not extend '" + getSimpleName(V1_ABSTRACT_TASK_MESSAGE_SEND) + "'."));
                }

                // Check 2: Implements JavaDelegate
                if (implementsInterface) {
                    issues.add(new BpmnElementLintItemSuccess(
                            elementId, bpmnFile, processId,
                            "SendTask implementation class '" + implClass + "' implements " + getSimpleName(V1_JAVA_DELEGATE) + "."));
                } else {
                    issues.add(new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateLintItem(
                            elementId, bpmnFile, processId, implClass,
                            "SendTask implementation class '" + implClass + "' does not implement '" + getSimpleName(V1_JAVA_DELEGATE) + "'."));
                }
            } else {
                // V2: Check interface implementation
                if (doesNotImplementCorrectInterface(implClass, projectRoot, apiVersion, BpmnElementType.SEND_TASK)) {
                    issues.add(new BpmnSendTaskNoInterfaceClassImplementingLintItem(
                            elementId, bpmnFile, processId,
                            "SendTask implementation class '" + implClass
                                    + "' does not implement MessageSendTask interface."));
                } else {
                    // Success
                    String implementedInterface = findImplementedInterface(implClass, projectRoot, apiVersion, BpmnElementType.SEND_TASK);
                    String interfaceName = implementedInterface != null
                            ? getSimpleName(implementedInterface)
                            : "MessageSendTask";

                    issues.add(new BpmnElementLintItemSuccess(
                            elementId, bpmnFile, processId,
                            "SendTask implementation class '" + implClass + "' implements " + interfaceName + "."));
                }
            }
        }

        // 3. Validate field injections
        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                sendTask, issues, bpmnFile, processId, projectRoot);

        // Check execution listener classes
        checkExecutionListenerClasses(sendTask, elementId, issues, bpmnFile, processId, projectRoot);
    }

    // ==================== USER TASK ====================

    /**
     * Lints a {@link UserTask} including formKey, task listeners, and execution listeners.
     * <p>
     * Validates the task name, formKey configuration, questionnaire references,
     * task listener classes, and execution listener classes if present.
     *
     * @param userTask  the UserTask to lint
     * @param issues    the list to collect lint items
     * @param bpmnFile  the BPMN file reference
     * @param processId the process identifier
     */
    public void lintUserTask(
            UserTask userTask,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = userTask.getId();
        var locator = FhirResourceLocator.create(projectRoot);

        // 1. Validate task name
        if (isEmpty(userTask.getName())) {
            issues.add(new BpmnUserTaskNameEmptyLintItem(
                    elementId, bpmnFile, processId));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "User Task has a non-empty name: '" + userTask.getName() + "'"));
        }

        // 2. Validate formKey
        String formKey = userTask.getCamundaFormKey();

        if (isEmpty(formKey)) {
            issues.add(new BpmnUserTaskFormKeyEmptyLintItem(
                    elementId, bpmnFile, processId));
        } else {
            boolean isExternalForm = formKey.startsWith("external:")
                    || formKey.startsWith("http://")
                    || formKey.startsWith("https://");

            if (!isExternalForm) {
                issues.add(new BpmnUserTaskFormKeyIsNotAnExternalFormLintItem(
                        elementId, bpmnFile, processId));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "User Task formKey is valid: '" + formKey + "'"));

                // Check questionnaire existence
                if (!locator.questionnaireExists(formKey, projectRoot)) {
                    issues.add(new FhirQuestionnaireDefinitionLintItem(
                            LinterSeverity.ERROR, elementId, bpmnFile, processId, formKey,
                            "User Task questionnaire not found for formKey: " + formKey));
                } else {
                    issues.add(new BpmnElementLintItemSuccess(
                            elementId, bpmnFile, processId,
                            "Questionnaire exists for formKey: '" + formKey + "'"));
                }
            }
        }

        // 3. Validate task listener classes
        checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Check execution listener classes (UserTasks can also have execution listeners)
        checkExecutionListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);
    }

    // ==================== RECEIVE TASK ====================

    /**
     * Lints a {@link ReceiveTask} including message definition validation.
     * <p>
     * Validates the task name, message definition, FHIR resource references,
     * and execution listener classes if present.
     *
     * @param receiveTask the ReceiveTask to lint
     * @param issues      the list to collect lint items
     * @param bpmnFile    the BPMN file reference
     * @param processId   the process identifier
     */
    public void lintReceiveTask(
            ReceiveTask receiveTask,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = receiveTask.getId();

        // Check if the ReceiveTask name is non-empty.
        if (isEmpty(receiveTask.getName()))
        {
            issues.add(new BpmnEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }
        else
        {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "ReceiveTask has a non-empty name: '" + receiveTask.getName() + "'"));
        }

        // Check if the message definition exists and its name is non-empty.
        if (receiveTask.getMessage() == null || isEmpty(receiveTask.getMessage().getName()))
        {
            issues.add(new BpmnMessageStartEventMessageNameEmptyLintItem(
                    elementId, bpmnFile, processId));
        }
        else
        {
            String msgName = receiveTask.getMessage().getName();
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "ReceiveTask message name is non-empty: '" + msgName + "'"));

            // Check FHIR references
            checkMessageName(msgName, issues, elementId, bpmnFile, processId, projectRoot);
        }

        // Check execution listener classes
        checkExecutionListenerClasses(receiveTask, elementId, issues, bpmnFile, processId, projectRoot);
    }
}