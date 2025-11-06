package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.FloatingElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.resource.FhirResourceLocatorFactory;
import org.camunda.bpm.model.bpmn.instance.ReceiveTask;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.UserTask;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkMessageName;
import static dev.dsf.linter.bpmn.BpmnElementLinter.checkTaskListenerClasses;
import static dev.dsf.linter.classloading.ClassInspector.classExists;
import static dev.dsf.linter.classloading.ClassInspector.implementsDsfTaskInterface;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * <h2>DSF BPMN Task linter</h2>
 *
 * <p>
 * The {@code BpmnTaskLinter} performs structural and semantic linting on BPMN
 * task elements defined in Camunda workflows. It lints four types of tasks:
 * {@link ServiceTask}, {@link UserTask}, {@link SendTask}, and {@link ReceiveTask}.
 * </p>
 *
 * <p>
 * Each lint method checks for required attributes, Camunda-specific extensions,
 * and integration consistency with FHIR resources where applicable. lint results
 * are appended to a list of {@link BpmnElementLintItem} instances.
 * </p>
 *
 * <h3>Supported linter Features:</h3>
 * <ul>
 *   <li><b>ServiceTask:</b> Name presence, implementation class existence, and JavaDelegate interface checks.</li>
 *   <li><b>UserTask:</b> Name, formKey linting, questionnaire resolution, and listener class checks.</li>
 *   <li><b>SendTask:</b> Name, implementation class existence/interface, and field injection lints.</li>
 *   <li><b>ReceiveTask:</b> Name, message definition integrity, and FHIR message name cross-checks.</li>
 * </ul>
 *
 * <h3>References:</h3>
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">Camunda Extensions</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 */
public class BpmnTaskLinter
{
    private final File projectRoot;

    /**
     * Constructs a new {@code BpmnTaskLinter} for linting BPMN task elements
     * within a given project directory.
     *
     * @param projectRoot the root directory of the project containing BPMN and FHIR resources
     */
    public BpmnTaskLinter(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    // SERVICE TASK LINTER

    /**
     * lints that a {@link ServiceTask} has a non-empty name, a non-empty
     * {@code camunda:class}, that the class can be loaded, and that it implements
     * {@code org.camunda.bpm.engine.delegate.JavaDelegate}.
     *
     * <p>
     * The linter checks include:
     * <ul>
     *   <li>If the task name is empty, a warning is recorded; if it is non-empty, a success item is added.</li>
     *   <li>If the implementation class is null or empty, a corresponding issue is recorded.</li>
     *   <li>If the implementation class cannot be found or does not implement JavaDelegate, an issue is recorded;
     *       otherwise, a success item is added indicating that the implementation is valid.</li>
     * </ul>
     * </p>
     *
     * @param task       the {@link ServiceTask} to lint
     * @param issues     the list to collect any identified lint items
     * @param bpmnFile   the .bpmn file for reference
     * @param processId  the process identifier for logging reference
     */
    public void lintServiceTask(
            ServiceTask task,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = task.getId();
        ApiVersion apiVersion = ApiVersionHolder.getVersion();
        // lint the task name.
        if (isEmpty(task.getName()))
        {
            issues.add(new BpmnServiceTaskNameEmptyLintItem(elementId, bpmnFile, processId));
        }
        else
        {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "ServiceTask has a non-empty name: '" + task.getName() + "'"
            ));
        }

        // Lint the implementation class.
        String implClass = task.getCamundaClass();
        if (implClass == null)
        {
            issues.add(new BpmnServiceTaskImplementationNotExistLintItem(elementId, bpmnFile, processId));
        }
        else if (implClass.trim().isEmpty())
        {
            issues.add(new BpmnServiceTaskImplementationClassEmptyLintItem(elementId, bpmnFile, processId));
        }
        else
        {
            if (!classExists(implClass, projectRoot))
            {
                issues.add(new BpmnServiceTaskImplementationClassNotFoundLintItem(
                        elementId, bpmnFile, processId, implClass));
            }
            else if (!implementsDsfTaskInterface(implClass, projectRoot))
            {
                if (apiVersion == ApiVersion.V1)
                  issues.add(new BpmnServiceTaskImplementationClassNotImplementingJavaDelegateLintItem(
                        elementId, bpmnFile, processId, implClass));

                if(apiVersion == ApiVersion.V2)
                {
                    issues.add(new BpmnServiceTaskNoInterfaceClassImplementingLintItem(
                            elementId, bpmnFile, processId,
                            "ServiceTask implementation class '" + implClass
                                    + "' exists but does not implement a supported DSF task interface."));
                }
            }
            else
            {
                if(apiVersion == ApiVersion.V1)
                    // Success: the implementation class exists and implements JavaDelegate.
                    issues.add(new BpmnElementLintItemSuccess(
                            elementId,
                            bpmnFile,
                            processId,
                            "ServiceTask implementation class '" + implClass + "' exists and implements JavaDelegate."
                    ));
                if(apiVersion == ApiVersion.V2)
                    issues.add(new BpmnElementLintItemSuccess(
                            elementId,
                            bpmnFile,
                            processId,
                            "ServiceTask implementation class '" + implClass + "' exists and implements a supported DSF task interface."
                    ));
            }
        }
    }


    // USER TASK LINTER

    /**
     * lints a {@link UserTask}, checking:
     * <ul>
     *   <li>That the task name is non-empty.</li>
     *   <li>That the {@code camunda:formKey} is non-empty and starts with {@code "external:"} or a valid URL.</li>
     *   <li>If the formKey references a known questionnaire resource.</li>
     *   <li>That any task listeners referencing a Java class exist on the classpath.
     *       (No success item is recorded for task listener linting.)</li>
     * </ul>
     * <p>
     * For each check that passes, a success item is recorded using {@code BpmnElementLintItemSuccess};
     * for failures, appropriate warning or error items are added.
     * </p>
     *
     * @param userTask  the {@link UserTask} to lint
     * @param issues    the list of lint items to which any detected issues or successes will be added
     * @param bpmnFile  the BPMN file for reference
     * @param processId the process identifier for logging reference
     */
    public void lintUserTask(
            UserTask userTask,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = userTask.getId();
        var locator = FhirResourceLocatorFactory.getResourceLocator(projectRoot);

        // lint that the User Task name is not empty.
        if (isEmpty(userTask.getName())) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "User Task name is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.WARN,
                    FloatingElementType.USER_TASK_NAME_IS_EMPTY
            ));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "User Task has a non-empty name: '" + userTask.getName() + "'"
            ));
        }

        // Lint the camunda:formKey.
        String formKey = userTask.getCamundaFormKey();
        boolean found = true;
        if (isEmpty(formKey)) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "User Task formKey is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.ERROR,
                    FloatingElementType.USER_TASK_FORM_KEY_IS_EMPTY
            ));
        } else {
            // The external form must either be marked with "external:" or be a URL (starting with "http://" or "https://").
            if (!(formKey.startsWith("external:") || formKey.startsWith("http://") || formKey.startsWith("https://"))) {
                issues.add(new BpmnFloatingElementLintItem(
                        elementId, bpmnFile, processId,
                        "User Task formKey is not an external form: " + formKey,
                        LintingType.BPMN_FLOATING_ELEMENT,
                        LinterSeverity.ERROR,
                        FloatingElementType.USER_TASK_FORM_KEY_IS_NOT_AN_EXTERNAL_FORM
                ));
                found = false;
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "User Task formKey is valid: '" + formKey + "'"
                ));
            }
            // If formKey is valid, check if the corresponding questionnaire exists.
            if (found) {
                if (!locator.questionnaireExists(formKey, projectRoot)) {
                    issues.add(new FhirQuestionnaireDefinitionLintItem(
                            LinterSeverity.ERROR, elementId, bpmnFile, processId,
                            formKey,
                            "User Task questionnaire not found for formKey: " + formKey
                    ));
                } else {
                    issues.add(new BpmnElementLintItemSuccess(
                            elementId,
                            bpmnFile,
                            processId,
                            "Questionnaire exists for formKey: '" + formKey + "'"
                    ));
                }
            }
        }

        // Lint any <camunda:taskListener> classes.
        checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);
    }

    // SEND TASK LINTER

    /**
     * lints a {@link SendTask}, checking:
     * <ul>
     *   <li>That the task name is non-empty – if empty, a warning is recorded; if non-empty, a success item is added.</li>
     *   <li>That the implementation class is non-empty, exists, and implements {@code JavaDelegate} –
     *       if any condition fails, an appropriate issue is recorded; otherwise, a success item is added.</li>
     *   <li>Field injections for {@code profile}, {@code messageName}, and {@code instantiatesCanonical}
     *       are linted (no success item is recorded for field injections).</li>
     * </ul>
     *
     * @param sendTask  the {@link SendTask} to lint
     * @param issues    the list of lint items to which any detected issues (warnings, errors, or success items) will be added
     * @param bpmnFile  the .bpmn file for reference
     * @param processId the process identifier for logging reference
     */
    public void lintSendTask(
            SendTask sendTask,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = sendTask.getId();
        ApiVersion apiVersion = ApiVersionHolder.getVersion();

        // lint the task name.
        if (isEmpty(sendTask.getName()))
        {
            issues.add(new BpmnEventNameEmptyLintItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        }
        else
        {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "SendTask has a non-empty name: '" + sendTask.getName() + "'"
            ));
        }

        // lint the implementation class.
        String implClass = sendTask.getCamundaClass();
        if (isEmpty(implClass))
        {
            issues.add(new BpmnMessageSendTaskImplementationClassEmptyLintItem(elementId, bpmnFile, processId));
        }
        else
        {
            if (!classExists(implClass, projectRoot))
            {
                issues.add(new BpmnMessageSendTaskImplementationClassNotFoundLintItem(
                        elementId, bpmnFile, processId, implClass));
            }
            else if (!implementsDsfTaskInterface(implClass, projectRoot))
            {
                if (apiVersion == ApiVersion.V1)
                {
                    issues.add(new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateLintItem(
                            elementId, bpmnFile, processId, implClass));
                }
                if(apiVersion == ApiVersion.V2){
                    issues.add(new BpmnSendTaskNoInterfaceClassImplementingLintItem(
                            elementId, bpmnFile, processId,
                            "Implementation class '" + implClass
                                    + "' exists but does not implement a supported DSF task interface."));
                }
            }
            else
            {
                if(apiVersion == ApiVersion.V1)
                    // Success: the implementation class exists and implements JavaDelegate.
                    issues.add(new BpmnElementLintItemSuccess(
                            elementId,
                            bpmnFile,
                            processId,
                            "SendTask implementation class '" + implClass + "' exists and implements JavaDelegate."
                    ));
                if(apiVersion == ApiVersion.V2)
                    issues.add(new BpmnElementLintItemSuccess(
                            elementId,
                            bpmnFile,
                            processId,
                            "SendTask implementation class '" + implClass + "' exists and implements a supported DSF task interface."
                    ));
            }
        }

        BpmnFieldInjectionLinter.lintMessageSendFieldInjections(
                sendTask, issues, bpmnFile, processId, projectRoot);
    }


    // RECEIVE TASK LINTER


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

            // Lint that the message name appears in the FHIR resources.
            checkMessageName(msgName, issues, elementId, bpmnFile, processId, projectRoot);
        }
    }

}
