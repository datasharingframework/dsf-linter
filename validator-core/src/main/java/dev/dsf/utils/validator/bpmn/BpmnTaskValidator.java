package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.FloatingElementType;
import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.ValidationType;
import dev.dsf.utils.validator.util.BpmnValidationUtils;
import dev.dsf.utils.validator.util.FhirValidator;
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
        if (BpmnValidationUtils.isEmpty(task.getName()))
        {
            issues.add(new BpmnServiceTaskNameEmptyValidationItem(elementId, bpmnFile, processId));
        }

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
            else if (!BpmnValidationUtils.implementsJavaDelegate(implClass, projectRoot))
            {
                issues.add(new BpmnServiceTaskImplementationClassNotImplementingJavaDelegateValidationItem(
                        elementId, bpmnFile, processId, implClass));
            }
        }
    }

    // USER TASK VALIDATION

    /**
     * Validates a {@link UserTask}, checking:
     * <ul>
     *   <li>Non-empty task name</li>
     *   <li>Non-empty {@code camunda:formKey} that starts with {@code "external:"}</li>
     *   <li>If the formKey references a known questionnaire resource</li>
     *   <li>Any task listeners referencing a Java class must exist on the classpath</li>
     * </ul>
     *
     * @param userTask   the {@link UserTask} to validate
     * @param issues     the list of validation items
     * @param bpmnFile   reference to BPMN file
     * @param processId  process id
     */
    public void validateUserTask(
            UserTask userTask,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = userTask.getId();

        // Check if the User Task name is empty.
        if (BpmnValidationUtils.isEmpty(userTask.getName())) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "User Task name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.WARN,
                    FloatingElementType.USER_TASK_NAME_IS_EMPTY
            ));
        }

        String formKey = userTask.getCamundaFormKey();
        boolean found = true;
        // Check if the formKey is empty.
        if (BpmnValidationUtils.isEmpty(formKey)) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "User Task formKey is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.ERROR,
                    FloatingElementType.USER_TASK_FORM_KEY_IS_EMPTY
            ));
            found= false;
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
                found= false;
            }
            // Check if the corresponding questionnaire exists.
            // Assumes that the project root is the current directory.
            if(found) {
            if (!FhirValidator.questionnaireExists(formKey, projectRoot)) {
                issues.add(new FhirQuestionnaireDefinitionValidationItem(
                        ValidationSeverity.ERROR, elementId, bpmnFile, processId,
                        formKey,
                        "User Task questionnaire not found for formKey: " + formKey
                ));
            }
            }
        }
    // Validate any <camunda:taskListener> classes
        BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);
    }

    // SEND TASK VALIDATION

    /**
     * Validates a {@link SendTask}, checking:
     * <ul>
     *   <li>Non-empty name</li>
     *   <li>Implementation class: non-empty, exists, implements {@code JavaDelegate}</li>
     *   <li>Field injections for {@code profile}, {@code messageName}, and {@code instantiatesCanonical}</li>
     * </ul>
     */
    public void validateSendTask(
            SendTask sendTask,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = sendTask.getId();

        if (BpmnValidationUtils.isEmpty(sendTask.getName()))
        {
            issues.add(new BpmnEventNameEmptyValidationItem(elementId, bpmnFile, processId, "'" + elementId + "' has no name"));
        }

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
            else if (!BpmnValidationUtils.implementsJavaDelegate(implClass, projectRoot))
            {
                issues.add(new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateValidationItem(
                        elementId, bpmnFile, processId, implClass));
            }
        }

        // Validate field injections
        BpmnFieldInjectionValidator.validateMessageSendFieldInjections(
                sendTask, issues, bpmnFile, processId, projectRoot);
    }

    // RECEIVE TASK VALIDATION

    /**
     * Validates a {@link ReceiveTask}, checking:
     * <ul>
     *   <li>Non-empty name</li>
     *   <li>Non-empty message definition {@code message.getName()}</li>
     *   <li>{@code message.getName()} must appear in ActivityDefinition/StructureDefinition</li>
     * </ul>
     */
    public void validateReceiveTask(
            ReceiveTask receiveTask,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = receiveTask.getId();

        if (BpmnValidationUtils.isEmpty(receiveTask.getName()))
        {
            issues.add(new BpmnEventNameEmptyValidationItem (elementId, bpmnFile, processId, "'" + elementId + "' has no name."));
        }

        if (receiveTask.getMessage() == null || BpmnValidationUtils.isEmpty(receiveTask.getMessage().getName()))
        {
            issues.add(new BpmnMessageStartEventMessageNameEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            String msgName = receiveTask.getMessage().getName();
            BpmnValidationUtils.checkMessageName(msgName, issues, elementId, bpmnFile, processId, projectRoot);
        }
    }
}
