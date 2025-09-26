package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.FloatingElementType;
import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.logger.ConsoleLogger;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.util.api.ApiVersion;
import dev.dsf.utils.validator.util.api.ApiVersionHolder;
import dev.dsf.utils.validator.util.resource.FhirResourceLocator;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static dev.dsf.utils.validator.classloading.ClassInspector.*;
import static dev.dsf.utils.validator.util.ValidationUtils.containsPlaceholder;
import static dev.dsf.utils.validator.util.ValidationUtils.isEmpty;

public class BpmnElementValidator {

    private static final Logger logger = new ConsoleLogger(false);

    /**
     * Validates the implementation class extracted from a BPMN element.
     * <p>
     * This method checks that the implementation class is non-empty, exists on the classpath,
     * and implements the {@code JavaDelegate} interface. Appropriate validation issues are added
     * if any of these checks fail. If all validations pass, a success item is recorded.
     * </p>
     *
     * @param implClass   the implementation class as a string
     * @param elementId   the identifier of the BPMN element being validated
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the element
     * @param issues      the list of {@link BpmnElementValidationItem} to which any validation issues or success items will be added
     * @param projectRoot the project root directory used for class loading
     */
    public static void validateImplementationClass(
            String implClass,
            String elementId,
            File bpmnFile,
            String processId,
            List<BpmnElementValidationItem> issues,
            File projectRoot)
    {
        String apiVersion = ApiVersionHolder.getVersion().toString();

        if (!classExists(implClass, projectRoot))
        {
            issues.add(new BpmnMessageSendEventImplementationClassNotFoundValidationItem(
                    elementId, bpmnFile, processId, implClass));
        }
        else if (!implementsDsfTaskInterface(implClass, projectRoot))
        {
            // only report this issue for v1
            if ("v1".equals(apiVersion))
            {
                issues.add(new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateValidationItem(
                        elementId, bpmnFile, processId, implClass));
            }
            if("v2".equals(apiVersion))
            {
                issues.add(new BpmnEndOrIntermediateThrowEventMissingInterfaceValidationItem(
                        elementId, bpmnFile, processId, implClass,
                        "Implementation class '" + implClass
                                + "' does not implement a supported DSF task interface."));
            }
        }
        else
        {
            if("v1".equals(apiVersion))
                // Success: the implementation class exists and implements JavaDelegate.
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "Implementation class '" + implClass + "' exists and implements JavaDelegate."
                ));
            if("v2".equals(apiVersion))
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "Implementation class '" + implClass + "' exists and implements a supported DSF task interface."
                ));
        }
    }


    /**
     * Checks if the given message name is recognized in FHIR resources.
     * <p>
     * This method verifies that the message name exists in at least one ActivityDefinition and one StructureDefinition.
     * If the message name is found, a success item is recorded; otherwise, corresponding validation issues are added.
     * </p>
     *
     * @param messageName the message name to check
     * @param issues      the list of {@link BpmnElementValidationItem} where validation issues or success items will be added
     * @param elementId   the identifier of the BPMN element being validated
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the element
     * @param projectRoot the project root directory containing FHIR resources
     */
    public static void checkMessageName(
            String messageName,
            List<BpmnElementValidationItem> issues,
            String elementId,
            File bpmnFile,
            String processId,
            File projectRoot)
    {
        // Check for a matching ActivityDefinition.
        if (FhirResourceLocator.activityDefinitionExists(messageName, projectRoot))
        {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "ActivityDefinition found for messageName: '" + messageName + "'"
            ));
        }
        else
        {
            issues.add(new FhirActivityDefinitionValidationItem(
                    ValidationSeverity.ERROR,
                    elementId,
                    bpmnFile,
                    processId,
                    messageName,
                    "No ActivityDefinition found for messageName: " + messageName
            ));
        }

        // Check for a matching StructureDefinition.
        if (FhirResourceLocator.structureDefinitionExists(messageName, projectRoot))
        {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "StructureDefinition found for messageName: '" + messageName + "'"
            ));
        }
        else
        {
            issues.add(new FhirStructureDefinitionValidationItem(
                    ValidationSeverity.ERROR,
                    elementId,
                    bpmnFile,
                    processId,
                    messageName,
                    "StructureDefinition [" + messageName + "] not found."
            ));
        }
    }


    /**
     * Checks if the given BPMN element has any {@link CamundaExecutionListener} with an implementation class
     * that cannot be found on the classpath.
     * <p>
     * This method inspects the extension elements of the BPMN element for execution listeners and verifies
     * that each specified class exists. For each listener:
     * <ul>
     *   <li>If the listener's implementation class is specified and cannot be found, an error item is added.</li>
     *   <li>If the listener's implementation class is specified and is found, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param element     the BPMN {@link BaseElement} to check
     * @param elementId   the identifier of the BPMN element being validated
     * @param issues      the list of {@link BpmnElementValidationItem} where validation issues or success items will be added
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the element
     * @param projectRoot the project root directory used for class loading
     */
    public static void checkExecutionListenerClasses(
            BaseElement element,
            String elementId,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot)
    {
        if (element.getExtensionElements() != null)
        {
            Collection<CamundaExecutionListener> listeners =
                    element.getExtensionElements().getElementsQuery()
                            .filterByType(CamundaExecutionListener.class)
                            .list();
            for (CamundaExecutionListener listener : listeners)
            {
                String implClass = listener.getCamundaClass();
                if (isEmpty(implClass))
                {
                    if (!classExists(implClass, projectRoot))
                    {
                        issues.add(new BpmnFloatingElementValidationItem(
                                elementId, bpmnFile, processId,
                                "Execution listener class not found: " + implClass,
                                ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                                ValidationSeverity.ERROR,
                                FloatingElementType.EXECUTION_LISTENER_CLASS_NOT_FOUND
                        ));
                    }
                    else
                    {
                        issues.add(new BpmnElementValidationItemSuccess(
                                elementId,
                                bpmnFile,
                                processId,
                                "Execution listener class found: " + implClass
                        ));
                    }
                }
            }
        }
    }


    public static void checkTaskListenerClasses(
            UserTask userTask,
            String elementId,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot) {
        if (userTask.getExtensionElements() == null) {
            return;
        }

        Collection<CamundaTaskListener> listeners = userTask.getExtensionElements()
                .getElementsQuery()
                .filterByType(CamundaTaskListener.class)
                .list();

        for (CamundaTaskListener listener : listeners) {
            String implClass = listener.getCamundaClass();

            // Step 1: Validate presence of the class attribute.
            if (isEmpty(implClass)) {
                issues.add(new BpmnUserTaskListenerMissingClassAttributeValidationItem(elementId, bpmnFile, processId));
                continue; // Skip further checks for this listener.
            } else {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "UserTask listener declares a class attribute: '" + implClass + "'"));
            }

            // Step 2: Validate class existence on the classpath.
            if (!classExists(implClass, projectRoot)) {
                issues.add(new BpmnUserTaskListenerJavaClassNotFoundValidationItem(
                        elementId, bpmnFile, processId, implClass));
                continue; // Skip further checks for this listener.
            } else {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "UserTask listener class '" + implClass + "' was found on the project classpath"));
            }

            // Step 3: Perform API-specific inheritance/interface checks.
            ApiVersion apiVersion = ApiVersionHolder.getVersion();
            String defaultSuperClass = null;
            String requiredInterface = null;

            // First, determine the required class names based on the API version.
            switch (apiVersion) {
                case V2:
                    defaultSuperClass = "dev.dsf.bpe.v2.activity.DefaultUserTaskListener";
                    requiredInterface = "dev.dsf.bpe.v2.activity.UserTaskListener";
                    break;
                case V1:
                    defaultSuperClass = "dev.dsf.bpe.v1.activity.DefaultUserTaskListener";
                    requiredInterface = "org.camunda.bpm.engine.delegate.TaskListener";
                    break;
                case UNKNOWN:
                    // Log or handle the case where the API version is unknown and no checks can be performed.
                    logger.debug("Unknown API version for UserTask listener validation. Skipping inheritance checks.");
                    break;
            }

            // Then, execute the validation logic once with the determined class names.
            if (defaultSuperClass != null) {
                extendsDefault(elementId, issues, bpmnFile, processId, projectRoot, implClass, defaultSuperClass, requiredInterface);
            }
        }
    }

    /**
     * Validates the TimerEventDefinition for an Intermediate Catch Event.
     * <p>
     * This method checks the timer expressions (timeDate, timeCycle, timeDuration) in the TimerEventDefinition.
     * It adds a validation issue if all timer expressions are empty. Otherwise, it records a success item
     * indicating that the timer type is provided. Then, it logs an informational issue if a fixed date/time is used,
     * or warns if a cycle/duration value appears fixed (i.e. contains no placeholder), and records a success item
     * if a valid placeholder is found.
     * </p>
     *
     * @param elementId the identifier of the BPMN element being validated
     * @param issues    the list of {@link BpmnElementValidationItem} to which validation issues or success items will be added
     * @param bpmnFile  the BPMN file under validation
     * @param processId the identifier of the BPMN process containing the event
     * @param timerDef  the {@link TimerEventDefinition} to validate
     */
    public static void checkTimerDefinition(
            String elementId,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId,
            TimerEventDefinition timerDef)
    {
        Expression timeDateExpr = timerDef.getTimeDate();
        Expression timeCycleExpr = timerDef.getTimeCycle();
        Expression timeDurationExpr = timerDef.getTimeDuration();

        boolean isTimeDateEmpty = (timeDateExpr == null || isEmpty(timeDateExpr.getTextContent()));
        boolean isTimeCycleEmpty = (timeCycleExpr == null || isEmpty(timeCycleExpr.getTextContent()));
        boolean isTimeDurationEmpty = (timeDurationExpr == null || isEmpty(timeDurationExpr.getTextContent()));

        if (isTimeDateEmpty && isTimeCycleEmpty && isTimeDurationEmpty)
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Timer type is empty (no timeDate, timeCycle, or timeDuration)",
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.ERROR,
                    FloatingElementType.TIMER_TYPE_IS_EMPTY
            ));
        }
        else
        {
            // Overall success: timer type is provided.
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "Timer type is provided."
            ));

            if (!isTimeDateEmpty)
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Timer type is a fixed date/time (timeDate) – please verify if this is intended",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.INFO,
                        FloatingElementType.TIMER_TYPE_IS_A_FIXED_DATE_TIME
                ));
                // Record a success specifically for timeDate.
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "Fixed date/time (timeDate) provided: '" + timeDateExpr.getTextContent() + "'"
                ));
            }
            else {
                String timerValue = !isTimeCycleEmpty ? timeCycleExpr.getTextContent() : timeDurationExpr.getTextContent();
                if (!containsPlaceholder(timerValue))
                {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Timer value appears fixed (no placeholder found)",
                            ValidationType.BPMN_FLOATING_ELEMENT,
                            ValidationSeverity.WARN,
                            FloatingElementType.TIMER_VALUE_APPEARS_FIXED_NO_PLACEHOLDER_FOUND
                    ));
                }
                else
                {
                    issues.add(new BpmnElementValidationItemSuccess(
                            elementId, bpmnFile, processId,
                            "Timer value with cycle/duration contains a valid placeholder: '" + timerValue + "'"
                    ));
                }
            }
        }
    }



    /**
     * Validates a {@link BoundaryEvent} that contains an {@link ErrorEventDefinition}.
     * <p>
     * The validation is split based on whether an error reference is provided:
     * <ul>
     *   <li>If the boundary event's name is empty, a warning is added; otherwise, a success item is recorded.</li>
     *   <li>If an error is provided, it checks that both the error name and error code are not empty:
     *       if either is empty, an error item is added; if provided, a success item is recorded for each.</li>
     *   <li>If the {@code errorCodeVariable} attribute is missing, a warning is added; otherwise, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param boundaryEvent the {@link BoundaryEvent} to validate
     * @param issues        the list of {@link BpmnElementValidationItem} to which validation issues or success items will be added
     * @param bpmnFile      the BPMN file under validation
     * @param processId     the identifier of the BPMN process containing the event
     */
    public static void checkErrorBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = boundaryEvent.getId();

        // 1. Check if the BoundaryEvent's name is empty.
        if (isEmpty(boundaryEvent.getName()))
        {
            issues.add(new BpmnErrorBoundaryEventNameEmptyValidationItem(
                    elementId, bpmnFile, processId
            ));
        }
        else
        {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "BoundaryEvent has a non-empty name: '" + boundaryEvent.getName() + "'"
            ));
        }

        // 2. Retrieve the ErrorEventDefinition.
        ErrorEventDefinition errorDef = (ErrorEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();

        // If an error is provided, check its name and error code.
        if (errorDef.getError() != null)
        {
            // 2a. Check the error name.
            if (isEmpty(errorDef.getError().getName()))
            {
                issues.add(new BpmnErrorBoundaryEventErrorNameEmptyValidationItem(
                        elementId, bpmnFile, processId
                ));
            }
            else
            {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "Error name is provided: '" + errorDef.getError().getName() + "'"
                ));
            }
            // 2b. Check the error code.
            if (isEmpty(errorDef.getError().getErrorCode()))
            {
                issues.add(new BpmnErrorBoundaryEventErrorCodeEmptyValidationItem(
                        elementId, bpmnFile, processId
                ));
            }
            else
            {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "Error code is provided: '" + errorDef.getError().getErrorCode() + "'"
                ));
            }
        }

        // 3. Check the errorCodeVariable attribute.
        String errorCodeVariable = errorDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "errorCodeVariable");
        if (isEmpty(errorCodeVariable))
        {
            issues.add(new BpmnErrorBoundaryEventErrorCodeVariableEmptyValidationItem(
                    elementId, bpmnFile, processId
            ));
        }
        else
        {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "errorCodeVariable is provided: '" + errorCodeVariable + "'"
            ));
        }
    }


    /**
     * Validates a {@link ConditionalEventDefinition} for an Intermediate Catch Event.
     * <p>
     * This method performs several checks:
     * <ul>
     *   <li>Warns if the event name is empty; otherwise, records a success item.</li>
     *   <li>Errors if the conditional event variable name is empty; otherwise, records a success item.</li>
     *   <li>Errors if the {@code variableEvents} attribute is empty; otherwise, records a success item.</li>
     *   <li>
     *       If the condition type attribute is empty but a condition expression is provided, it assumes "expression" and records a success item.
     *       If the condition type is provided but is not "expression", an informational issue is logged and a success item is recorded.
     *       If the condition type is "expression", a success item is recorded.
     *   </li>
     *   <li>
     *       If the condition type is "expression" and the condition expression is empty, an error is recorded;
     *       otherwise, a success item is recorded.
     *   </li>
     * </ul>
     * </p>
     *
     * @param catchEvent the Conditional Intermediate Catch Event to validate
     * @param issues     the list of {@link BpmnElementValidationItem} to which validation issues or success items will be added
     * @param bpmnFile   the BPMN file associated with the event
     * @param processId  the BPMN process identifier containing the event
     */
    public static void checkConditionalEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = catchEvent.getId();

        // 1. Check event name.
        String eventName = catchEvent.getName();
        if (isEmpty(eventName)) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.WARN,
                    FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_NAME_IS_EMPTY
            ));
        } else {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event name is provided: '" + eventName + "'"
            ));
        }

        // 2. Get the ConditionalEventDefinition (assuming the first event definition is ConditionalEventDefinition).
        ConditionalEventDefinition condDef =
                (ConditionalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        // 3. Check conditional event variable name.
        String variableName = condDef.getCamundaVariableName();
        if (isEmpty(variableName)) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variable name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.ERROR,
                    FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_VARIABLE_NAME_IS_EMPTY
            ));
        } else {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variable name is provided: '" + variableName + "'"
            ));
        }

        // 4. Check variableEvents attribute.
        String variableEvents = condDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "variableEvents");
        if (isEmpty(variableEvents)) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variableEvents is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.ERROR,
                    FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_VARIABLE_EVENTS_IS_EMPTY
            ));
        } else {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variableEvents is provided: '" + variableEvents + "'"
            ));
        }

        // 5. Check conditionType attribute.
        String conditionType = condDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "conditionType");

        if (isEmpty(conditionType)) {
            if (condDef.getCondition() != null && !isEmpty(condDef.getCondition().getRawTextContent())) {
                conditionType = "expression";
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "Condition type assumed to be 'expression' as condition expression is provided."
                ));
            } else {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Conditional Intermediate Catch Event condition type is empty",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.ERROR,
                        FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_CONDITION_TYPE_IS_EMPTY
                ));
            }
        } else if (!"expression".equalsIgnoreCase(conditionType)) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is not 'expression': " + conditionType,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.INFO,
                    FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_CONDITION_TYPE_IS_NOT_EXPRESSION
            ));
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "Condition type is provided and is not 'expression': '" + conditionType + "'"
            ));
        } else {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is 'expression'"
            ));
        }

        // 6. Check condition expression (only if condition type is 'expression').
        if ("expression".equalsIgnoreCase(conditionType)) {
            if (condDef.getCondition() == null || isEmpty(condDef.getCondition().getRawTextContent())) {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Conditional Intermediate Catch Event expression is empty",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.ERROR,
                        FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_EXPRESSION_IS_EMPTY
                ));
            } else {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "Conditional Intermediate Catch Event expression is provided: '" + condDef.getCondition().getRawTextContent() + "'"
                ));
            }
        }
    }


    /**
     * Checks the "profile" field value for validity.
     * <p>
     * This method verifies that the profile field is not empty, contains a version placeholder,
     * and corresponds to an existing FHIR StructureDefinition. If any check fails, an appropriate
     * validation issue is added. Additionally, if a check passes, a success item is recorded.
     * </p>
     *
     * @param elementId   the identifier of the BPMN element being validated
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the element
     * @param issues      the list of {@link BpmnElementValidationItem} to which validation issues or success items will be added
     * @param literalValue the literal value of the profile field from the BPMN element
     * @param projectRoot the project root directory containing FHIR resources
     */
    public static void checkProfileField(
            String elementId,
            File bpmnFile,
            String processId,
            List<BpmnElementValidationItem> issues,
            String literalValue,
            File projectRoot)
    {
        if (isEmpty(literalValue))
        {
            issues.add(new BpmnFieldInjectionProfileEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else
        {
            // Record success that the profile field is provided.
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "Profile field is provided with value: '" + literalValue + "'"
            ));

            if (!containsPlaceholder(literalValue))
            {
                issues.add(new BpmnFieldInjectionProfileNoVersionPlaceholderValidationItem(
                        elementId, bpmnFile, processId, literalValue));
            }
            else
            {
                // Record success that the version placeholder is present.
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "Profile field contains a version placeholder: '" + literalValue + "'"
                ));
            }
            if (!FhirResourceLocator.structureDefinitionExists(literalValue, projectRoot))
            {
                issues.add(new FhirStructureDefinitionValidationItem(
                        ValidationSeverity.WARN,
                        elementId,
                        bpmnFile,
                        processId,
                        literalValue,
                        "StructureDefinition for the profile: [" + literalValue + "] not found."
                ));
            }
            else
            {
                // Record success that the StructureDefinition exists.
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "StructureDefinition found for profile: '" + literalValue + "'"
                ));
            }
        }
    }


    /**
     * Checks the "instantiatesCanonical" field value for validity.
     * <p>
     * This method ensures that the instantiatesCanonical field is not empty and contains a version placeholder.
     * If the field is empty, a validation issue is added. Similarly, if the version placeholder is missing,
     * a corresponding validation issue is added. If both conditions are met (i.e. the field is non-empty and
     * contains a valid placeholder), a success item is recorded.
     * </p>
     *
     * @param elementId   the identifier of the BPMN element being validated
     * @param literalValue the literal value of the instantiatesCanonical field
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the element
     * @param issues      the list of {@link BpmnElementValidationItem} where validation issues or success items will be added
     * @param projectRoot the project root directory containing FHIR resources
     */
    public static void checkInstantiatesCanonicalField(
            String elementId,
            String literalValue,
            File bpmnFile,
            String processId,
            List<BpmnElementValidationItem> issues,
            File projectRoot)
    {
        if (isEmpty(literalValue))
        {
            issues.add(new BpmnFieldInjectionInstantiatesCanonicalEmptyValidationItem(
                    elementId, bpmnFile, processId));
        }
        else
        {
            if (!containsPlaceholder(literalValue))
            {
                issues.add(new BpmnFieldInjectionInstantiatesCanonicalNoVersionPlaceholderValidationItem(
                        elementId, bpmnFile, processId));
            }
            else
            {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId,
                        bpmnFile,
                        processId,
                        "instantiatesCanonical field is valid with value: '" + literalValue + "'"
                ));
            }
        }
    }

    private static void extendsDefault(String elementId, List<BpmnElementValidationItem> issues, File bpmnFile, String processId, File projectRoot, String implClass, String defaultSuperClass, String requiredInterface) {
        boolean extendsDefault = isSubclassOf(implClass, defaultSuperClass, projectRoot);
        boolean implementsInterface = implementsInterface(implClass, requiredInterface, projectRoot);

        String inheritanceDescription = extendsDefault ? "extends " + defaultSuperClass : "implements " + requiredInterface;
        if (extendsDefault || implementsInterface)
        {
            issues.add(new BpmnElementValidationItemSuccess(
                    elementId, bpmnFile, processId,
                    "UserTask listener '" + implClass +  "' extend or implement the required interface class: '" + inheritanceDescription + "'"));
        }
        else {
            issues.add(new BpmnUserTaskListenerNotExtendingOrImplementingRequiredClassValidationItem (
                    elementId, bpmnFile, processId, implClass,
                    "UserTask listener '" + implClass + "' does not extend the default class '" + defaultSuperClass
                            + "' or implement the required interface '" + requiredInterface + "'."));
        }
    }

}
