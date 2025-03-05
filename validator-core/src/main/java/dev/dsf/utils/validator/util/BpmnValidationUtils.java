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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;

/**
 * <p>
 * The {@code BpmnValidationUtils} class contains common static methods used across
 * the various BPMN validator classes. These utility methods handle string checks,
 * class-loading checks, conditional event checks, and cross-references to FHIR
 * resources.
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
public class BpmnValidationUtils
{
    /**
     * Checks if the given string is null or empty.
     *
     * @param value the string to check
     * @return true if the string is null or empty, false otherwise
     */
    public static boolean isEmpty(String value)
    {
        return (value == null || value.trim().isEmpty());
    }

    /**
     * Checks if a string contains the {@code #{version}} placeholder.
     */
    public static boolean containsVersionPlaceholder(String rawValue)
    {
        return (rawValue != null && rawValue.contains("#{version}"));
    }

    /**
     * Attempts to read any nested {@code <camunda:string>} text content if the
     * {@code camunda:stringValue} is not set directly on the {@code CamundaField}.
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
     * Checks if a fully-qualified class name can be loaded either from the
     * context class loader or from a fallback {@link URLClassLoader} built
     * from {@code target/classes} and/or {@code build/classes} plus
     * {@code target/dependency} JARs.
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
     * Creates a {@link URLClassLoader} that includes /target/classes (or /build/classes)
     * and /target/dependency JARs (if any).
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
     * Checks if the given class name implements {@code org.camunda.bpm.engine.delegate.JavaDelegate}.
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
     * Checks whether a formKey presumably references a questionnaire. Here, it simply checks
     * for the substring "questionnaire".
     */
    public static boolean questionnaireExists(String formKey)
    {
        return formKey != null && formKey.contains("questionnaire");
    }

    /**
     * Extracts the {@code camunda:class} attribute from a BPMN event, searching both
     * the element itself and any attached {@link MessageEventDefinition}.
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
     * Validates the extracted {@code camunda:class}, checking if it is non-empty, exists,
     * and implements JavaDelegate.
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
     * Checks that the given message name is recognized in FHIR ActivityDefinition and
     * StructureDefinition, adding appropriate validation items if missing.
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
     * Checks if a BPMN element has any {@link CamundaExecutionListener} referencing a class
     * that cannot be found on the classpath.
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
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND
                    ));
                }
            }
        }
    }

    /**
     * Checks if a BPMN user task has any {@link CamundaTaskListener} referencing a class
     * that cannot be found on the classpath.
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
                            ValidationType.BPMN_MESSAGE_SEND_EVENT_IMPLEMENTATION_CLASS_NOT_FOUND
                    ));
                }
            }
        }
    }

    /**
     * Validates the TimerEventDefinition for an Intermediate Catch Event.
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
                if (!containsVersionPlaceholder(timerValue))
                {
                    issues.add(new BpmnFloatingElementValidationItem(
                            elementId, bpmnFile, processId,
                            "Timer value appears fixed (no version placeholder found)",
                            ValidationType.BPMN_FLOATING_ELEMENT
                    ));
                }
            }
        }
    }

    /**
     * Validates a {@link BoundaryEvent} containing an {@link ErrorEventDefinition}.
     * Splits logic based on whether {@code errorRef} is set:
     * <ul>
     *   <li>If {@code errorRef} is present, checks name/code are not empty => ERROR if empty.</li>
     *   <li>Boundary name empty => WARN.</li>
     *   <li>{@code errorCodeVariable} missing => WARN.</li>
     * </ul>
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
     * Validates a {@link ConditionalEventDefinition} for an IntermediateCatchEvent.
     * Checks:
     * <ul>
     *   <li>{@code camunda:variableName} is not empty</li>
     *   <li>{@code camunda:variableEvents} is not empty</li>
     *   <li>{@code camunda:conditionType} is 'expression' (INFO if not)</li>
     *   <li>Condition expression is not empty</li>
     * </ul>
     */
    public static void checkConditionalEvent(
            IntermediateCatchEvent catchEvent,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId)
    {
        String elementId = catchEvent.getId();
        ConditionalEventDefinition condDef =
                (ConditionalEventDefinition) catchEvent.getEventDefinitions().iterator().next();

        String variableName = condDef.getCamundaVariableName();
        if (isEmpty(variableName))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variable name is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        String variableEvents = condDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "variableEvents");
        if (isEmpty(variableEvents))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event variableEvents is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }

        String conditionType = condDef.getAttributeValueNs(
                "http://camunda.org/schema/1.0/bpmn",
                "conditionType");
        if (isEmpty(conditionType))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
        else if (!"expression".equalsIgnoreCase(conditionType))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event condition type is not 'expression': " + conditionType,
                    ValidationType.BPMN_FLOATING_ELEMENT,
                    ValidationSeverity.INFO
            ));
        }

        if (condDef.getCondition() == null || isEmpty(condDef.getCondition().getRawTextContent()))
        {
            issues.add(new BpmnFloatingElementValidationItem(
                    elementId, bpmnFile, processId,
                    "Conditional Intermediate Catch Event expression is empty",
                    ValidationType.BPMN_FLOATING_ELEMENT
            ));
        }
    }

    /**
     * Checks whether all parts of a camelCase message name are found (in order) within
     * a profile string (ignoring case).
     */
    public static boolean doesProfileContainMessageNameParts(String profileValue, String messageNameValue)
    {
        if (profileValue == null || messageNameValue == null)
        {
            return false;
        }

        String[] parts = messageNameValue.split("(?=[A-Z])");
        String profileLower = profileValue.toLowerCase();
        int lastFoundIndex = -1;

        for (String part : parts)
        {
            String lowerPart = part.toLowerCase();
            int foundIndex = profileLower.indexOf(lowerPart, lastFoundIndex + 1);
            if (foundIndex == -1)
            {
                return false;
            }
            lastFoundIndex = foundIndex;
        }
        return true;
    }

    /**
     * Checks the "profile" field for emptiness, version placeholders, and existence
     * in FHIR StructureDefinition.
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
            if (!containsVersionPlaceholder(literalValue))
            {
                issues.add(new BpmnFieldInjectionProfileNoVersionPlaceholderValidationItem(
                        elementId, bpmnFile, processId, literalValue));
            }
            if (!FhirValidator.structureDefinitionExists(literalValue, projectRoot))
            {
                issues.add(new FhirStructureDefinitionValidationItem(ValidationSeverity.ERROR,
                        elementId,
                        bpmnFile,
                        processId,
                        literalValue,
                        "StructureDefinition for the profile [" + literalValue + "] not found "
                ));
            }
        }
    }

    /**
     * Checks the "instantiatesCanonical" field for emptiness, version placeholder presence.
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
            if (!containsVersionPlaceholder(literalValue))
            {
                issues.add(new BpmnFieldInjectionInstantiatesCanonicalNoVersionPlaceholderValidationItem(
                        elementId, bpmnFile, processId));
            }
        }
    }

    // BpmnValidationUtils.java (near the bottom)
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
