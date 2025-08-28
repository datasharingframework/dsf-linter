package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.FloatingElementType;
import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.ValidationType;
import dev.dsf.utils.validator.util.BpmnValidationUtils;
import dev.dsf.utils.validator.util.FhirValidator;
import dev.dsf.utils.validator.util.ApiVersionHolder;
import org.camunda.bpm.model.bpmn.instance.ReceiveTask;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.UserTask;

import java.io.File;
import java.util.List;

/**
 * <p>
 * The {@code BpmnTaskValidator} class handles validation logic for various BPMN tasks:
 * ServiceTask, UserTask, SendTask, and ReceiveTask.
 * </p>
 *
 * <p>
 * References:
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">Camunda Extension Elements</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 * </p>
 */
public class BpmnTaskValidator
{
    private final File projectRoot;

    public BpmnTaskValidator(File projectRoot)
    {
        this.projectRoot = projectRoot;
    }

    // SERVICE TASK VALIDATION

    /**
     * Validates that a {@link ServiceTask} has a non-empty name, a non-empty
     * {@code camunda:class}, that the class can be loaded, and that it implements
     * {@code org.camunda.bpm.engine.delegate.JavaDelegate}.
     *
     * <p>
     * The validation checks include:
     * <ul>
     *   <li>If the task name is empty, a warning is recorded; if it is non-empty, a success item is added.</li>
     *   <li>If the implementation class is null or empty, a corresponding issue is recorded.</li>
     *   <li>If the implementation class cannot be found or does not implement JavaDelegate, an issue is recorded;
     *       otherwise, a success item is added indicating that the implementation is valid.</li>
     * </ul>
     * </p>
     *
     * @param task       the {@link ServiceTask} to validate
     * @param issues     the list to collect any identified validation items
     * @param bpmnFile   the .bpmn file for reference
     * @param processId  the process identifier for logging reference
     */
    public void validateServiceTask(
            ServiceTask task,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = task.getId();
        String apiVersion = ApiVersionHolder.getVersion();
        // Validate the task name.
        if (BpmnValidationUtils.isEmpty(task.getName()))
        {
            issues.add(new BpmnServiceTaskNameEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "ServiceTask has a non-empty name: '" + task.getName() + "'"
            ));
        }

        // Validate the implementation class.
        String implClass = task.getCamundaClass();
        if (implClass == null)
        {
            issues.add(new BpmnServiceTaskImplementationNotExistValidationItem(elementId, bpmnFile, processId));
        }
        else if (implClass.trim().isEmpty())
        {
            issues.add(new BpmnServiceTaskImplementationClassEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            if (!BpmnValidationUtils.classExists(implClass, projectRoot))
            {
                issues.add(new BpmnServiceTaskImplementationClassNotFoundValidationItem(
                        elementId, bpmnFile, processId, implClass));
            }
            else if (BpmnValidationUtils.implementsDsfTaskInterface(implClass, projectRoot))
            {
                if ("v1".equals(apiVersion))
                  issues.add(new BpmnServiceTaskImplementationClassNotImplementingJavaDelegateValidationItem(
                        elementId, bpmnFile, processId, implClass));
            }
            else
            {
                // Success: the implementation class exists and implements JavaDelegate.
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "ServiceTask implementation class '" + implClass + "' exists and implements JavaDelegate."
                ));
            }
        }
    }


    // USER TASK VALIDATION

    /**
     * Validates a {@link UserTask}, checking:
     * <ul>
     *   <li>That the task name is non-empty.</li>
     *   <li>That the {@code camunda:formKey} is non-empty and starts with {@code "external:"} or a valid URL.</li>
     *   <li>If the formKey references a known questionnaire resource.</li>
     *   <li>That any task listeners referencing a Java class exist on the classpath.
     *       (No success item is recorded for task listener validation.)</li>
     * </ul>
     * <p>
     * For each check that passes, a success item is recorded using {@code BpmnElementValidationItemSuccess};
     * for failures, appropriate warning or error items are added.
     * </p>
     *
     * @param userTask  the {@link UserTask} to validate
     * @param issues    the list of validation items to which any detected issues or successes will be added
     * @param bpmnFile  the BPMN file for reference
     * @param processId the process identifier for logging reference
     */
    public void validateUserTask(
            UserTask userTask,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = userTask.getId();

        // Validate that the User Task name is not empty.
        if (BpmnValidationUtils.isEmpty(userTask.getName())) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "User Task name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.WARN,
                    FloatingElementType.USER_TASK_NAME_IS_EMPTY
            ));
        } else {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "User Task has a non-empty name: '" + userTask.getName() + "'"
            ));
        }

        // Validate the camunda:formKey.
        String formKey = userTask.getCamundaFormKey();
        boolean found = true;
        if (BpmnValidationUtils.isEmpty(formKey)) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "User Task formKey is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.ERROR,
                    FloatingElementType.USER_TASK_FORM_KEY_IS_EMPTY
            ));
        } else {
            // The external form must either be marked with "external:" or be a URL (starting with "http://" or "https://").
            if (!(formKey.startsWith("external:") || formKey.startsWith("http://") || formKey.startsWith("https://"))) {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "User Task formKey is not an external form: " + formKey,
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.ERROR,
                        FloatingElementType.USER_TASK_FORM_KEY_IS_NOT_AN_EXTERNAL_FORM
                ));
                found = false;
            } else {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "User Task formKey is valid: '" + formKey + "'"
                ));
            }
            // If formKey is valid, check if the corresponding questionnaire exists.
            if (found) {
                if (!FhirValidator.questionnaireExists(formKey, projectRoot)) {
                    issues.add(new FhirQuestionnaireDefinitionValidationItem(
                            ValidationSeverity.ERROR, elementId, bpmnFile, processId,
                            formKey,
                            "User Task questionnaire not found for formKey: " + formKey
                    ));
                } else {
                    issues.add(new BpmnElementValidationItemSuccess(
                            elementId,
                            bpmnFile,
                            processId,
                            "Questionnaire exists for formKey: '" + formKey + "'"
                    ));
                }
            }
        }

        // Validate any <camunda:taskListener> classes.
        BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);
    }

    // SEND TASK VALIDATION

    /**
     * Validates a {@link SendTask}, checking:
     * <ul>
     *   <li>That the task name is non-empty – if empty, a warning is recorded; if non-empty, a success item is added.</li>
     *   <li>That the implementation class is non-empty, exists, and implements {@code JavaDelegate} –
     *       if any condition fails, an appropriate issue is recorded; otherwise, a success item is added.</li>
     *   <li>Field injections for {@code profile}, {@code messageName}, and {@code instantiatesCanonical}
     *       are validated (no success item is recorded for field injections).</li>
     * </ul>
     *
     * @param sendTask  the {@link SendTask} to validate
     * @param issues    the list of validation items to which any detected issues (warnings, errors, or success items) will be added
     * @param bpmnFile  the .bpmn file for reference
     * @param processId the process identifier for logging reference
     */
    public void validateSendTask(
            SendTask sendTask,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = sendTask.getId();
        String apiVersion = ApiVersionHolder.getVersion();

        // Validate the task name.
        if (BpmnValidationUtils.isEmpty(sendTask.getName()))
        {
            issues.add(new BpmnEventNameEmptyValidationItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        }
        else
        {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "SendTask has a non-empty name: '" + sendTask.getName() + "'"
            ));
        }

        // Validate the implementation class.
        String implClass = sendTask.getCamundaClass();
        if (BpmnValidationUtils.isEmpty(implClass))
        {
            issues.add(new BpmnMessageSendTaskImplementationClassEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            if (!BpmnValidationUtils.classExists(implClass, projectRoot))
            {
                issues.add(new BpmnMessageSendTaskImplementationClassNotFoundValidationItem(
                        elementId, bpmnFile, processId, implClass));
            }
            else if (BpmnValidationUtils.implementsDsfTaskInterface(implClass, projectRoot))
            {
                // only report this issue for v1
                if ("v1".equals(apiVersion))
                {
                    issues.add(new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateValidationItem(
                            elementId, bpmnFile, processId, implClass));
                }
            }
            else
            {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "Implementation class '" + implClass
                                + "' exists and implements a supported DSF task interface."));
            }
        }

        //todo Validate field injections (no success item is added for field injections)
        BpmnFieldInjectionValidator.validateMessageSendFieldInjections(
                sendTask, issues, bpmnFile, processId, projectRoot);
    }


    // RECEIVE TASK VALIDATION

    /**
     * Validates a {@link ReceiveTask}, checking:
     * <ul>
     *   <li>That the task name is non-empty – if empty, a warning is recorded; if non-empty, a success item is added.</li>
     *   <li>That the message definition exists and its {@code message.getName()} is non-empty – if empty, a warning is recorded;
     *       if non-empty, a success item is added.</li>
     *   <li>That the message name appears in ActivityDefinition/StructureDefinition (validated via {@link BpmnValidationUtils#checkMessageName(String, List, String, File, String, File)}),
     *       with no additional success item for that check.</li>
     * </ul>
     *
     * @param receiveTask the {@link ReceiveTask} to validate
     * @param issues      the list of validation items to which any detected issues or successes will be added
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the task
     */
    public void validateReceiveTask(
            ReceiveTask receiveTask,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = receiveTask.getId();

        // Check if the ReceiveTask name is non-empty.
        if (BpmnValidationUtils.isEmpty(receiveTask.getName()))
        {
            issues.add(new BpmnEventNameEmptyValidationItem(
                    elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }
        else
        {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "ReceiveTask has a non-empty name: '" + receiveTask.getName() + "'"));
        }

        // Check if the message definition exists and its name is non-empty.
        if (receiveTask.getMessage() == null || BpmnValidationUtils.isEmpty(receiveTask.getMessage().getName()))
        {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(
                    elementId, bpmnFile, processId));
        }
        else
        {
            String msgName = receiveTask.getMessage().getName();
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "ReceiveTask message name is non-empty: '" + msgName + "'"));

            // Validate that the message name appears in the FHIR resources.
            BpmnValidationUtils.checkMessageName(msgName, issues, elementId, bpmnFile, processId, projectRoot);
        }
    }

}
