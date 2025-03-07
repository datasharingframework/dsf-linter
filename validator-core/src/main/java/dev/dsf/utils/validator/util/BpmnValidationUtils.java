package dev.dsf.utils.validator.util;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import dev.dsf.utils.validator.fhir.FhirValidator;
import dev.dsf.utils.validator.item.*;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaField;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;

/**
 * The {@code BpmnValidationUtils} class contains common static utility methods used across various BPMN validator classes.
 * <p>
 * These utility methods support operations such as:
 * <ul>
 *   <li>String null or emptiness checks</li>
 *   <li>Placeholder detection within strings</li>
 *   <li>Extraction of nested string content from Camunda fields</li>
 *   <li>Class-loading and existence checks, including verifying if a class implements {@code JavaDelegate}</li>
 *   <li>Extraction of implementation class values from BPMN elements</li>
 *   <li>Validation of implementation classes and listener classes</li>
 *   <li>Cross-checking message names against FHIR resources</li>
 *   <li>XML parsing of FHIR resource files</li>
 *   <li>Timer, error boundary, and conditional event validation checks</li>
 *   <li>Validation of profile and instantiatesCanonical field values against FHIR StructureDefinition and ActivityDefinition</li>
 *   <li>Checking if a BPMN profile string contains parts of a message name</li>
 * </ul>
 * </p>
 *
 * <p>
 * References:
 * <ul>
 *   <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 *   <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">
 *       Camunda Extension Elements</a></li>
 *   <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 *   <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 * </p>
 */
public class BpmnValidationUtils
{
    /**
     * Checks if the given string is null or empty (after trimming).
     *
     * @param value the string to check
     * @return {@code true} if the string is null or empty; {@code false} otherwise
     */
    public static boolean isEmpty(String value)
    {
        return (value == null || value.trim().isEmpty());
    }

    /**
     * Checks if the given string contains a version placeholder.
     * <p>
     * A valid placeholder is expected to be in the format "${someWord}" or "#{someWord}", with at least one character inside.
     * </p>
     *
     * @param rawValue the string to check for a placeholder
     * @return {@code true} if the string contains a valid placeholder; {@code false} otherwise
     */
    private static boolean containsPlaceholder(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return false;
        }
        // Regex explanation:
        // (\\$|#)      : Matches either a '$' or '#' character.
        // "\\{"        : Matches the literal '{'.
        // "[^\\}]+":   : Ensures that at least one character (that is not '}') is present.
        // "\\}"        : Matches the literal '}'.
        // ".*" before and after allows the placeholder to appear anywhere in the string.
        return rawValue.matches(".*(?:\\$|#)\\{[^\\}]+\\}.*");
    }

    /**
     * Attempts to read any nested {@code <camunda:string>} text content from a {@link CamundaField}.
     * <p>
     * If the field does not have a direct string value set via {@code camunda:stringValue},
     * this method inspects its DOM children for a {@code <camunda:string>} element and returns its text content.
     * </p>
     *
     * @param field the {@link CamundaField} to extract nested string content from
     * @return the text content from a nested {@code <camunda:string>} element, or {@code null} if not found
     */
    public static String tryReadNestedStringContent(CamundaField field)
    {
        if (field == null) return null;
        DomElement domEl = field.getDomElement();
        if (domEl != null)
        {
            Collection<DomElement> childEls = domEl.getChildElements();
            for (DomElement child : childEls)
            {
                if ("string".equals(child.getLocalName())
                        && "http://camunda.org/schema/1.0/bpmn".equals(child.getNamespaceURI()))
                {
                    return child.getTextContent();
                }
            }
        }
        return null;
    }

    /**
     * Checks if a fully-qualified class name can be loaded from the current context or via a custom class loader.
     * <p>
     * This method first attempts to load the class using the context class loader. If that fails,
     * it falls back to a custom {@link URLClassLoader} that includes the project's classes and dependency JARs.
     * </p>
     *
     * @param className   the fully-qualified name of the class to load
     * @param projectRoot the project root directory to use for creating the custom class loader
     * @return {@code true} if the class can be loaded; {@code false} otherwise
     */
    public static boolean classExists(String className, File projectRoot)
    {
        // First, try context class loader
        try
        {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            cl.loadClass(className);
            return true;
        }
        catch (ClassNotFoundException e)
        {
            // fallback
        }
        catch (Throwable t)
        {
            System.err.println("DEBUG: Throwable while trying context CL: " + t.getMessage());
        }

        if (projectRoot != null)
        {
            try
            {
                ClassLoader urlCl = createProjectClassLoader(projectRoot);
                urlCl.loadClass(className);
                return true;
            }
            catch (Throwable t)
            {
                System.err.println("DEBUG: Throwable while custom loading " + className + ": " + t.getMessage());
            }
        }
        return false;
    }

    /**
     * Creates a {@link URLClassLoader} that includes the project's class directories and dependency JARs.
     * <p>
     * The method checks for the existence of "/target/classes" or "/build/classes" and adds them to the classpath.
     * Additionally, if a "/target/dependency" directory exists, all JAR files within it are also added.
     * </p>
     *
     * @param projectRoot the root directory of the project
     * @return a {@link URLClassLoader} that loads classes from the specified directories and JARs
     * @throws Exception if an error occurs while constructing the class loader
     */
    private static ClassLoader createProjectClassLoader(File projectRoot)
            throws Exception
    {
        File classesDir = new File(projectRoot, "target/classes");
        if (!classesDir.exists())
        {
            classesDir = new File(projectRoot, "build/classes");
        }

        List<URL> urlList = new java.util.ArrayList<>();
        if (classesDir.exists())
        {
            urlList.add(classesDir.toURI().toURL());
        }

        File dependencyDir = new File(projectRoot, "target/dependency");
        if (dependencyDir.exists() && dependencyDir.isDirectory())
        {
            File[] jars = dependencyDir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
            if (jars != null)
            {
                for (File jar : jars)
                {
                    urlList.add(jar.toURI().toURL());
                }
            }
        }

        URL[] urls = urlList.toArray(new URL[0]);
        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Checks if the class with the given name implements {@code org.camunda.bpm.engine.delegate.JavaDelegate}.
     * <p>
     * The check is performed by loading both the candidate class and the {@code JavaDelegate} interface using
     * a custom class loader, then verifying assignability.
     * </p>
     *
     * @param className   the fully-qualified class name to check
     * @param projectRoot the project root directory used to create the custom class loader
     * @return {@code true} if the candidate class implements {@code JavaDelegate}; {@code false} otherwise
     */
    public static boolean implementsJavaDelegate(String className, File projectRoot)
    {
        try
        {
            ClassLoader customCl = createProjectClassLoader(projectRoot);
            Class<?> candidateClass = Class.forName(className, true, customCl);
            Class<?> delegateInterface = Class.forName("org.camunda.bpm.engine.delegate.JavaDelegate", true, customCl);
            return delegateInterface.isAssignableFrom(candidateClass);
        }
        catch (Throwable t)
        {
            System.err.println("DEBUG: Exception in implementsJavaDelegate for " + className + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * Extracts the implementation class specified on a BPMN element.
     * <p>
     * The method first checks if the BPMN element has an attribute named "class" in the Camunda namespace.
     * If not found, and if the element is a {@link ThrowEvent} or {@link EndEvent} with a {@link MessageEventDefinition},
     * it will check the Camunda class specified within the message event definition.
     * </p>
     *
     * @param element the BPMN {@link BaseElement} from which to extract the implementation class
     * @return the implementation class as a string, or an empty string if not found
     */
    public static String extractImplementationClass(BaseElement element)
    {
        String implClass = element.getAttributeValueNs("class", "http://camunda.org/schema/1.0/bpmn");
        if (!isEmpty(implClass))
        {
            return implClass;
        }

        if (element instanceof ThrowEvent throwEvent)
        {
            for (EventDefinition def : throwEvent.getEventDefinitions())
            {
                if (def instanceof MessageEventDefinition msgDef)
                {
                    implClass = msgDef.getCamundaClass();
                    if (!isEmpty(implClass))
                    {
                        return implClass;
                    }
                }
            }
        }
        else if (element instanceof EndEvent endEvent)
        {
            for (EventDefinition def : endEvent.getEventDefinitions())
            {
                if (def instanceof MessageEventDefinition msgDef)
                {
                    implClass = msgDef.getCamundaClass();
                    if (!isEmpty(implClass))
                    {
                        return implClass;
                    }
                }
            }
        }
        return "";
    }

    /**
     * Validates the implementation class extracted from a BPMN element.
     * <p>
     * This method checks that the implementation class is non-empty, exists on the classpath,
     * and implements the {@code JavaDelegate} interface. Appropriate validation issues are added if any of these checks fail.
     * </p>
     *
     * @param implClass   the implementation class as a string
     * @param elementId   the identifier of the BPMN element being validated
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the element
     * @param issues      the list of {@link BpmnElementValidationItem} to which any validation issues will be added
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
        if (isEmpty(implClass))
        {
            issues.add(new BpmnMessageSendEventImplementationClassEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else if (!classExists(implClass, projectRoot))
        {
            issues.add(new BpmnMessageSendEventImplementationClassNotFoundValidationItem(
                    elementId, bpmnFile, processId, implClass));
        }
        else if (!implementsJavaDelegate(implClass, projectRoot))
        {
            issues.add(new BpmnMessageSendEventImplementationClassNotImplementingJavaDelegateValidationItem(
                    elementId, bpmnFile, processId, implClass));
        }
    }

    /**
     * Checks if the given message name is recognized in FHIR resources.
     * <p>
     * This method verifies that the message name exists in at least one ActivityDefinition and one StructureDefinition.
     * If not, it adds corresponding validation issues.
     * </p>
     *
     * @param messageName the message name to check
     * @param issues      the list of {@link BpmnElementValidationItem} where validation issues will be added
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
        if (!FhirValidator.activityDefinitionExists(messageName, projectRoot))
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
        if (!FhirValidator.structureDefinitionExists(messageName, projectRoot))
        {
            issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.ERROR,
                    elementId,
                    bpmnFile,
                    processId,
                    messageName,
                    "StructureDefinition [" + messageName + "] not found."
            ));
        }
    }

    /**
     * Checks if a BPMN element has any {@link CamundaExecutionListener} with an implementation class
     * that cannot be found on the classpath.
     * <p>
     * This method inspects the extension elements of the BPMN element for execution listeners and verifies
     * that each specified class exists.
     * </p>
     *
     * @param element     the BPMN {@link BaseElement} to check
     * @param elementId   the identifier of the BPMN element being validated
     * @param issues      the list of {@link BpmnElementValidationItem} where validation issues will be added
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
                if (!isEmpty(implClass) && !classExists(implClass, projectRoot))
                {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Execution listener class not found: " + implClass,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                            ValidationSeverity.ERROR
                    ));
                }
            }
        }
    }

    /**
     * Checks if a BPMN user task has any {@link CamundaTaskListener} with an implementation class
     * that cannot be found on the classpath.
     * <p>
     * The method inspects the extension elements of the {@link UserTask} for task listeners and verifies
     * the existence of their specified implementation classes.
     * </p>
     *
     * @param userTask    the {@link UserTask} to check
     * @param elementId   the identifier of the BPMN element being validated
     * @param issues      the list of {@link BpmnElementValidationItem} where validation issues will be added
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the task
     * @param projectRoot the project root directory used for class loading
     */
    public static void checkTaskListenerClasses(
            UserTask userTask,
            String elementId,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot)
    {
        if (userTask.getExtensionElements() != null)
        {
            Collection<CamundaTaskListener> listeners =
                    userTask.getExtensionElements().getElementsQuery()
                            .filterByType(CamundaTaskListener.class)
                            .list();
            for (CamundaTaskListener listener : listeners)
            {
                String implClass = listener.getCamundaClass();
                if (!isEmpty(implClass) && !classExists(implClass, projectRoot))
                {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Task listener class not found: " + implClass,
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND,
                            ValidationSeverity.ERROR
                    ));
                }
            }
        }
    }

    /**
     * Validates the TimerEventDefinition for an Intermediate Catch Event.
     * <p>
     * This method checks the timer expressions (timeDate, timeCycle, timeDuration) in the TimerEventDefinition.
     * It adds a validation issue if all timer expressions are empty, logs an informational issue if a fixed date/time
     * is used, or warns if a cycle/duration value appears fixed (i.e. contains no placeholder).
     * </p>
     *
     * @param elementId the identifier of the BPMN element being validated
     * @param issues    the list of {@link BpmnElementValidationItem} to which validation issues will be added
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
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
        else
        {
            if (!isTimeDateEmpty)
            {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Timer type is a fixed date/time (timeDate) – please verify if this is intended",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.INFO
                ));
            }
            else if (!isTimeCycleEmpty || !isTimeDurationEmpty)
            {
                String timerValue = !isTimeCycleEmpty ? timeCycleExpr.getTextContent()
                        : timeDurationExpr.getTextContent();
                if (!containsPlaceholder(timerValue))
                {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Timer value appears fixed (no placeholder found)",
                            ValidationType.BPMN_FLOATING_ELEMENT
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
     *   <li>If an error reference is present, it checks that both the error name and error code are not empty; errors are raised if they are.</li>
     *   <li>If the boundary event's name is empty, a warning is added.</li>
     *   <li>If the {@code errorCodeVariable} attribute is missing, a warning is added.</li>
     * </ul>
     * </p>
     *
     * @param boundaryEvent the {@link BoundaryEvent} to validate
     * @param issues        the list of {@link BpmnElementValidationItem} to which validation issues will be added
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

        if (isEmpty(boundaryEvent.getName()))
        {
            issues.add(new BpmnErrorBoundaryEventNameEmptyValidationItem(
                    elementId, bpmnFile, processId
            ));
        }

        ErrorEventDefinition errorDef =
                (ErrorEventDefinition) boundaryEvent.getEventDefinitions().iterator().next();

        if (errorDef.getError() != null)
        {
            if (isEmpty(errorDef.getError().getName()))
            {
                issues.add(new BpmnErrorBoundaryEventErrorNameEmptyValidationItem(
                        elementId, bpmnFile, processId
                ));
            }
            if (isEmpty(errorDef.getError().getErrorCode()))
            {
                issues.add(new BpmnErrorBoundaryEventErrorCodeEmptyValidationItem(
                        elementId, bpmnFile, processId
                ));
            }
        }

        String errorCodeVariable = errorDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "errorCodeVariable");
        if (isEmpty(errorCodeVariable))
        {
            issues.add(new BpmnErrorBoundaryEventErrorCodeVariableEmptyValidationItem(
                    elementId, bpmnFile, processId
            ));
        }
    }

    /**
     * Validates a {@link ConditionalEventDefinition} for an Intermediate Catch Event.
     * <p>
     * This method performs several checks:
     * <ul>
     *   <li>Warns if the event name is empty.</li>
     *   <li>Errors if the conditional event variable name is empty.</li>
     *   <li>Errors if the {@code variableEvents} attribute is empty.</li>
     *   <li>Errors if the conditional event condition type is empty (unless a condition expression is provided),
     *       and logs an informational issue if the condition type is not "expression".</li>
     *   <li>Errors if the condition expression is empty when the condition type is "expression".</li>
     * </ul>
     * </p>
     *
     * @param catchEvent the Conditional Intermediate Catch Event to validate
     * @param issues     the list of {@link BpmnElementValidationItem} to which validation issues will be added
     * @param bpmnFile   the BPMN file associated with the event
     * @param processId  the BPMN process identifier containing the event
     */
    public static void checkConditionalEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId) {

        String elementId = catchEvent.getId();

        // Check event name - warn if empty
        String eventName = catchEvent.getName();
        if (isEmpty(eventName)) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        // Get the conditional event definition (assuming the first event definition is of type ConditionalEventDefinition)
        ConditionalEventDefinition condDef =
                (ConditionalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        // Check conditional event variable name - error if empty
        String variableName = condDef.getCamundaVariableName();
        if (isEmpty(variableName)) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variable name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.ERROR
            ));
        }

        // Check variableEvents attribute - error if empty
        String variableEvents = condDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "variableEvents");
        if (isEmpty(variableEvents)) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variableEvents is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.ERROR
            ));
        }

        // Check conditionType attribute
        String conditionType = condDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "conditionType");

        // If conditionType is empty, but a condition expression is defined, assume "expression"
        if (isEmpty(conditionType)) {
            if (condDef.getCondition() != null && !isEmpty(condDef.getCondition().getRawTextContent())) {
                conditionType = "expression";
            } else {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Conditional Intermediate Catch Event condition type is empty",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.ERROR
                ));
            }
        } else if (!"expression".equalsIgnoreCase(conditionType)) {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is not 'expression': " + conditionType,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.INFO
            ));
        }

        // Check condition expression only if condition type is 'expression'
        if ("expression".equalsIgnoreCase(conditionType)) {
            if (condDef.getCondition() == null || isEmpty(condDef.getCondition().getRawTextContent())) {
                issues.add(new BpmnFloatingElementValidationItem(
                        elementId, bpmnFile, processId,
                        "Conditional Intermediate Catch Event expression is empty",
                        ValidationType.BPMN_FLOATING_ELEMENT,
                        ValidationSeverity.ERROR
                ));
            }
        }
    }

    /**
     * Checks the "profile" field value for validity.
     * <p>
     * This method verifies that the profile field is not empty, contains a version placeholder,
     * and corresponds to an existing FHIR StructureDefinition. If any check fails, an appropriate
     * validation issue is added.
     * </p>
     *
     * @param elementId   the identifier of the BPMN element being validated
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the element
     * @param issues      the list of {@link BpmnElementValidationItem} to which validation issues will be added
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
            if (!containsPlaceholder(literalValue))
            {
                issues.add(new BpmnFieldInjectionProfileNoVersionPlaceholderValidationItem(
                        elementId, bpmnFile, processId, literalValue));
            }
            if (!FhirValidator.structureDefinitionExists(literalValue, projectRoot))
            {
                issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.WARN,
                        elementId,
                        bpmnFile,
                        processId,
                        literalValue,
                        "StructureDefinition for the profile : [" + literalValue + "] not found "
                ));
            }
        }
    }

    /**
     * Checks the "instantiatesCanonical" field value for validity.
     * <p>
     * This method ensures that the instantiatesCanonical field is not empty and contains a version placeholder.
     * If the field is empty, a validation issue is added. Similarly, if the version placeholder is missing,
     * a corresponding validation issue is added.
     * </p>
     *
     * @param elementId   the identifier of the BPMN element being validated
     * @param literalValue the literal value of the instantiatesCanonical field
     * @param bpmnFile    the BPMN file under validation
     * @param processId   the identifier of the BPMN process containing the element
     * @param issues      the list of {@link BpmnElementValidationItem} where validation issues will be added
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
        }
    }

    // BpmnValidationUtils.java (near the bottom)
    /**
     * Parses an XML file into a {@link org.w3c.dom.Document}.
     * <p>
     * This method creates a namespace-aware {@link DocumentBuilder} and parses the provided XML file.
     * It returns the resulting {@link Document} or throws an exception if an error occurs.
     * </p>
     *
     * @param xmlFile the XML file to parse
     * @return the parsed {@link org.w3c.dom.Document}
     * @throws Exception if an error occurs during parsing
     */
    public static org.w3c.dom.Document parseXml(File xmlFile) throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        try (FileInputStream fis = new FileInputStream(xmlFile))
        {
            return db.parse(fis);
        }
    }

}
