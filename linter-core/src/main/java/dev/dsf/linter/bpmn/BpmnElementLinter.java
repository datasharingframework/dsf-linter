package dev.dsf.linter.bpmn;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.output.FloatingElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.resource.FhirResourceLocator;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static dev.dsf.linter.classloading.ClassInspector.*;
import static dev.dsf.linter.constants.DsfApiConstants.*;
import static dev.dsf.linter.util.linting.LintingUtils.containsPlaceholder;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Utility class providing element-level linting methods with proper API version isolation.
 * 
 * <p>
 * The {@code BpmnElementLinter} class serves as a collection of static utility methods for validating
 * various BPMN element configurations. It provides reusable validation logic that is shared across
 * multiple specialized linter classes, ensuring consistent validation behavior throughout the linting
 * framework. The class handles validation of message names, execution listeners, task listeners,
 * timer definitions, conditional events, error boundary events, and field injections.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * This class is designed as a utility class with static methods that can be called from specialized
 * linter classes such as {@link BpmnTaskLinter}, {@link BpmnEventLinter}, and {@link BpmnGatewayAndFlowLinter}.
 * The methods are organized by validation category and use specific LintItem classes for different
 * validation issues, ensuring proper categorization and reporting.
 * </p>
 *
 * <h2>Validation Categories</h2>
 * <p>
 * The class provides validation methods for the following categories:
 * </p>
 *
 * <h3>Message Name Validation</h3>
 * <ul>
 *   <li><strong>FHIR Resource Validation</strong>: Validates that message names correspond to existing
 *       FHIR ActivityDefinition and StructureDefinition resources in the project</li>
 *   <li><strong>Cross-Reference Validation</strong>: Ensures that message names are properly defined
 *       in both ActivityDefinition and StructureDefinition resources</li>
 * </ul>
 *
 * <h3>Execution Listener Validation</h3>
 * <ul>
 *   <li><strong>Class Existence</strong>: Verifies that execution listener classes exist on the project classpath</li>
 *   <li><strong>Interface Implementation</strong>: Validates that execution listener classes implement
 *       the correct interface based on the API version and element type</li>
 *   <li><strong>API Version Isolation</strong>: Uses version-specific interface requirements to ensure
 *       compatibility with the correct DSF BPE API version</li>
 * </ul>
 *
 * <h3>Task Listener Validation</h3>
 * <ul>
 *   <li><strong>Class Attribute Presence</strong>: Validates that task listeners declare a class attribute</li>
 *   <li><strong>Class Existence</strong>: Verifies that task listener classes exist on the project classpath</li>
 *   <li><strong>Inheritance Validation</strong>: Validates that task listener classes extend the default
 *       superclass or implement the required interface based on the API version</li>
 * </ul>
 *
 * <h3>Timer Definition Validation</h3>
 * <ul>
 *   <li><strong>Timer Type Validation</strong>: Ensures that timer events have at least one timer type
 *       (timeDate, timeCycle, or timeDuration) defined</li>
 *   <li><strong>Placeholder Validation</strong>: Validates that timer values contain placeholders for
 *       dynamic configuration (except for fixed date/time expressions)</li>
 *   <li><strong>Fixed Date/Time Detection</strong>: Issues informational warnings for fixed date/time
 *       expressions that may not be suitable for production use</li>
 * </ul>
 *
 * <h3>Error Boundary Event Validation</h3>
 * <ul>
 *   <li><strong>Event Name Validation</strong>: Validates that error boundary events have non-empty names</li>
 *   <li><strong>Error Definition Validation</strong>: Validates that error definitions include error names
 *       and error codes</li>
 *   <li><strong>Error Code Variable Validation</strong>: Ensures that error boundary events specify
 *       an errorCodeVariable for proper error handling</li>
 * </ul>
 *
 * <h3>Conditional Event Validation</h3>
 * <ul>
 *   <li><strong>Event Name Validation</strong>: Validates that conditional intermediate catch events have names</li>
 *   <li><strong>Variable Name Validation</strong>: Ensures that conditional events specify a variable name</li>
 *   <li><strong>Variable Events Validation</strong>: Validates that variableEvents attribute is properly configured</li>
 *   <li><strong>Condition Type and Expression Validation</strong>: Validates that condition types are
 *       properly set and that condition expressions are provided when required</li>
 * </ul>
 *
 * <h3>Field Injection Validation</h3>
 * <ul>
 *   <li><strong>Profile Field Validation</strong>: Validates that profile field injections are non-empty,
 *       contain version placeholders, and reference existing FHIR StructureDefinition resources</li>
 *   <li><strong>InstantiatesCanonical Field Validation</strong>: Validates that instantiatesCanonical
 *       field injections are non-empty and contain version placeholders</li>
 * </ul>
 *
 * <h2>Floating Element Detection</h2>
 * <p>
 * For floating elements (Intermediate Catch Events without incoming sequence flows), this class uses
 * {@link BpmnFloatingElementLintItem} with appropriate {@link FloatingElementType} values to categorize
 * and report issues. This helps identify elements that may be disconnected from the process flow.
 * </p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * File projectRoot = new File("/path/to/project");
 * 
 * // Validate message name
 * List<BpmnElementLintItem> issues = new ArrayList<>();
 * BpmnElementLinter.checkMessageName(
 *     "myMessage", issues, "elementId", bpmnFile, "processId", projectRoot);
 * 
 * // Validate execution listener
 * BpmnElementLinter.checkExecutionListenerClasses(
 *     element, "elementId", issues, bpmnFile, "processId", projectRoot);
 * 
 * // Validate task listener
 * BpmnElementLinter.checkTaskListenerClasses(
 *     userTask, "elementId", issues, bpmnFile, "processId", projectRoot);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe as it only contains static methods with no shared mutable state.
 * All methods operate on their parameters and do not maintain any internal state.
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">Camunda Extension Elements</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 *
 * @see BpmnFloatingElementLintItem
 * @see FloatingElementType
 * @see BpmnExecutionListenerClassNotFoundLintItem
 * @see BpmnUserTaskListenerMissingClassAttributeLintItem
 * @see BpmnUserTaskListenerJavaClassNotFoundLintItem
 * @see BpmnUserTaskListenerNotExtendingOrImplementingRequiredClassLintItem
 * @since 1.0
 */
public class BpmnElementLinter {

    //  MESSAGE NAME VALIDATION

    /**
     * Checks the message name against FHIR resources.
     */
    public static void checkMessageName(
            String messageName,
            List<BpmnElementLintItem> issues,
            String elementId,
            File bpmnFile,
            String processId,
            File projectRoot)
    {
        var locator = FhirResourceLocator.create(projectRoot);

        // Check for a matching ActivityDefinition.
        if (locator.activityDefinitionExists(messageName, projectRoot))
        {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "ActivityDefinition found for messageName: '" + messageName + "'"
            ));
        }
        else
        {
            issues.add(new BpmnNoActivityDefinitionFoundForMessageLintItem(
                    LinterSeverity.ERROR,
                    elementId,
                    bpmnFile,
                    processId,
                    messageName,
                    "No ActivityDefinition found for messageName: " + messageName
            ));
        }

        // Check for a matching StructureDefinition.
        if (locator.structureDefinitionExists(messageName, projectRoot))
        {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId,
                    bpmnFile,
                    processId,
                    "StructureDefinition found for messageName: '" + messageName + "'"
            ));
        }
        else
        {
            issues.add(new BpmnNoStructureDefinitionFoundForMessageLintItem(
                    LinterSeverity.ERROR,
                    elementId,
                    bpmnFile,
                    processId,
                    messageName,
                    "StructureDefinition [" + messageName + "] not found."
            ));
        }
    }

    //  EXECUTION LISTENER VALIDATION

    /**
     * Checks execution listener classes on a BPMN element with element-specific interface validation.
     */
    public static void checkExecutionListenerClasses(
            BaseElement element,
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot)
    {
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

    // ==================== TASK LISTENER VALIDATION ====================

    /**
     * Checks task listener classes with VERSION-ISOLATED interface validation.
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

    // ==================== TIMER DEFINITION VALIDATION ====================

    /**
     * Checks timer definition for intermediate catch events.
     */
    public static void checkTimerDefinition(
            String elementId,
            List<BpmnElementLintItem> issues,
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
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Timer type is empty (no timeDate, timeCycle, or timeDuration)",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.ERROR,
                    FloatingElementType.TIMER_TYPE_IS_EMPTY));
            return;
        }

        issues.add(new BpmnElementLintItemSuccess(
                elementId, bpmnFile, processId, "Timer type is provided."));

        if (!isTimeDateEmpty) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Timer type is a fixed date/time (timeDate) – please verify if this is intended",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.INFO,
                    FloatingElementType.TIMER_TYPE_IS_A_FIXED_DATE_TIME));
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Fixed date/time (timeDate) provided: '" + timeDateExpr.getTextContent() + "'"));
        } else {
            String timerValue = !isTimeCycleEmpty
                    ? timeCycleExpr.getTextContent()
                    : timeDurationExpr.getTextContent();

            if (!containsPlaceholder(timerValue)) {
                issues.add(new BpmnFloatingElementLintItem(
                        elementId, bpmnFile, processId,
                        "Timer value appears fixed (no placeholder found)",
                        LintingType.BPMN_FLOATING_ELEMENT,
                        LinterSeverity.WARN,
                        FloatingElementType.TIMER_VALUE_APPEARS_FIXED_NO_PLACEHOLDER_FOUND));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "Timer value contains a valid placeholder: '" + timerValue + "'"));
            }
        }
    }

    // ==================== ERROR BOUNDARY EVENT VALIDATION ====================

    /**
     * lints a {@link BoundaryEvent} that contains an {@link ErrorEventDefinition}.
     * <p>
     * The linter is split based on whether an error reference is provided:
     * <ul>
     *   <li>If the boundary event's name is empty, a warning is added; otherwise, a success item is recorded.</li>
     *   <li>If an error is provided, it checks that both the error name and error code are not empty:
     *       if either is empty, an error item is added; if provided, a success item is recorded for each.</li>
     *   <li>If the {@code errorCodeVariable} attribute is missing, a warning is added; otherwise, a success item is recorded.</li>
     * </ul>
     * </p>
     *
     * @param boundaryEvent the {@link BoundaryEvent} to lint
     * @param issues        the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile      the BPMN file under lint
     * @param processId     the identifier of the BPMN process containing the event
     */
    public static void checkErrorBoundaryEvent(
            BoundaryEvent boundaryEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = boundaryEvent.getId();

        // 1. Check if the BoundaryEvent's name is empty.
        if (isEmpty(boundaryEvent.getName()))
        {
            issues.add(new BpmnErrorBoundaryEventNameEmptyLintItem(
                    elementId, bpmnFile, processId
            ));
        }
        else
        {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "BoundaryEvent has a non-empty name: '" + boundaryEvent.getName() + "'"));
        }

        // 2. Retrieve the ErrorEventDefinition.
        ErrorEventDefinition errorDef = (ErrorEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();

        // If an error is provided, check its name and error code.
        if (errorDef.getError() != null)
        {
            // 2a. Check the error name.
            if (isEmpty(errorDef.getError().getName()))
            {
                issues.add(new BpmnErrorBoundaryEventErrorNameEmptyLintItem(
                        elementId, bpmnFile, processId
                ));
            }
            else
            {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "Error name is provided: '" + errorDef.getError().getName() + "'"
                ));
            }
            // 2b. Check the error code.
            if (isEmpty(errorDef.getError().getErrorCode()))
            {
                issues.add(new BpmnErrorBoundaryEventErrorCodeEmptyLintItem(
                        elementId, bpmnFile, processId
                ));
            }
            else
            {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "Error code is provided: '" + errorDef.getError().getErrorCode() + "'"));
            }
        }

        // 3. Check the errorCodeVariable attribute.
        String errorCodeVariable = errorDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "errorCodeVariable");
        if (isEmpty(errorCodeVariable))
        {
            issues.add(new BpmnErrorBoundaryEventErrorCodeVariableEmptyLintItem(
                    elementId, bpmnFile, processId
            ));
        }
        else
        {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "errorCodeVariable is provided: '" + errorCodeVariable + "'"
            ));
        }
    }

    // ==================== CONDITIONAL EVENT VALIDATION ====================

    /**
     * lints a {@link ConditionalEventDefinition} for an Intermediate Catch Event.
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
     * @param catchEvent the Conditional Intermediate Catch Event to lintate
     * @param issues     the list of {@link BpmnElementLintItem} to which linter issues or success items will be added
     * @param bpmnFile   the BPMN file associated with the event
     * @param processId  the BPMN process identifier containing the event
     */
    public static void checkConditionalEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = catchEvent.getId();

        // 1. Check event name
        String eventName = catchEvent.getName();
        if (isEmpty(eventName)) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event name is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.WARN,
                    FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_NAME_IS_EMPTY));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event name is provided: '" + eventName + "'"));
        }

        // 2. Get the ConditionalEventDefinition (assuming the first event definition is ConditionalEventDefinition).
        ConditionalEventDefinition condDef =
                (ConditionalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        // 3. Check conditional event variable name.
        String variableName = condDef.getCamundaVariableName();
        if (isEmpty(variableName)) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variable name is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.ERROR,
                    FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_VARIABLE_NAME_IS_EMPTY));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variable name is provided: '" + variableName + "'"
            ));
        }

        // 4. Check variableEvents attribute.
        String variableEvents = condDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "variableEvents");
        if (isEmpty(variableEvents)) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variableEvents is empty",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.ERROR,
                    FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_VARIABLE_EVENTS_IS_EMPTY));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
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
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "Condition type assumed to be 'expression' as condition expression is provided."));
            } else {
                issues.add(new BpmnFloatingElementLintItem(
                        elementId, bpmnFile, processId,
                        "Conditional Intermediate Catch Event condition type is empty",
                        LintingType.BPMN_FLOATING_ELEMENT,
                        LinterSeverity.ERROR,
                        FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_CONDITION_TYPE_IS_EMPTY));
            }
        } else if (!"expression".equalsIgnoreCase(conditionType)) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is not 'expression': " + conditionType,
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.INFO,
                    FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_CONDITION_TYPE_IS_NOT_EXPRESSION));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is 'expression'"));
        }

        // 6. Check condition expression (only if condition type is 'expression').
        if ("expression".equalsIgnoreCase(conditionType)) {
            if (condDef.getCondition() == null || isEmpty(condDef.getCondition().getRawTextContent())) {
                issues.add(new BpmnFloatingElementLintItem(
                        elementId, bpmnFile, processId,
                        "Conditional Intermediate Catch Event expression is empty",
                        LintingType.BPMN_FLOATING_ELEMENT,
                        LinterSeverity.ERROR,
                        FloatingElementType.CONDITIONAL_INTERMEDIATE_CATCH_EVENT_EXPRESSION_IS_EMPTY));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "Condition expression is provided: '" + condDef.getCondition().getRawTextContent() + "'"));
            }
        }
    }

    // ==================== FIELD INJECTION VALIDATION ====================

    /**
     * Checks the "profile" field value for validity.
     * <p>
     * This method verifies that the profile field is not empty, contains a version placeholder,
     * and corresponds to an existing FHIR StructureDefinition. If any check fails, an appropriate
     * lint issue is added. Additionally, if a check passes, a success item is recorded.
     * </p>
     *
     * @param elementId   the identifier of the BPMN element being lintated
     * @param bpmnFile    the BPMN file under lint
     * @param processId   the identifier of the BPMN process containing the element
     * @param issues      the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param literalValue the literal value of the profile field from the BPMN element
     * @param projectRoot the project root directory containing FHIR resources
     */
    public static void checkProfileField(
            String elementId,
            File bpmnFile,
            String processId,
            List<BpmnElementLintItem> issues,
            String literalValue,
            File projectRoot)
    {
        var locator = FhirResourceLocator.create(projectRoot);
        if (isEmpty(literalValue))
        {
            issues.add(new BpmnFieldInjectionProfileEmptyLintItem(elementId, bpmnFile, processId));
            return;
        }

        issues.add(new BpmnElementLintItemSuccess(
                elementId, bpmnFile, processId,
                "Profile field is provided with value: '" + literalValue + "'"));

        if (!containsPlaceholder(literalValue)) {
            issues.add(new BpmnFieldInjectionProfileNoVersionPlaceholderLintItem(
                    elementId, bpmnFile, processId, literalValue));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Profile field contains a version placeholder: '" + literalValue + "'"));
        }

        if (!locator.structureDefinitionExists(literalValue, projectRoot)) {
            issues.add(new BpmnNoStructureDefinitionFoundForMessageLintItem(
                    LinterSeverity.WARN, elementId, bpmnFile, processId, literalValue,
                    "StructureDefinition for the profile: [" + literalValue + "] not found."));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "StructureDefinition found for profile: '" + literalValue + "'"));
        }
    }

    /**
     * Checks instantiatesCanonical field injection.
     */
    public static void checkInstantiatesCanonicalField(
            String elementId,
            String literalValue,
            File bpmnFile,
            String processId,
            List<BpmnElementLintItem> issues,
            File projectRoot) {

        if (isEmpty(literalValue)) {
            issues.add(new BpmnFieldInjectionInstantiatesCanonicalEmptyLintItem(
                    elementId, bpmnFile, processId));
            return;
        }

        if (!containsPlaceholder(literalValue)) {
            issues.add(new BpmnFieldInjectionInstantiatesCanonicalNoVersionPlaceholderLintItem(
                    elementId, bpmnFile, processId));
        } else {
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "instantiatesCanonical field is valid with value: '" + literalValue + "'"));
        }
    }
}