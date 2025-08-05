package dev.dsf.utils.validator.util;

import dev.dsf.utils.validator.FloatingElementType;
import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import dev.dsf.utils.validator.item.*;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.lang.Error;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
     * List of all DSF task-related interface class names supported for validation purposes.
     * <p>
     * This array includes:
     * <ul>
     *   <li><b>API v1:</b> {@code org.camunda.bpm.engine.delegate.JavaDelegate}</li>
     *   <li><b>API v2:</b> {@code dev.dsf.bpe.v2.activity.ServiceTask},
     *       {@code dev.dsf.bpe.v2.activity.MessageSendTask},
     *       {@code dev.dsf.bpe.v2.activity.MessageIntermediateThrowEvent},
     *       {@code dev.dsf.bpe.v2.activity.MessageEndEvent},
     *       as well as {@code dev.dsf.bpe.v2.activity.UserTaskListener}</li>
     * </ul>
     * These interfaces define valid implementations for service tasks, message events, and user task listeners
     * in both legacy and modern DSF process definitions.
     * </p>
     */
    private static final String[] DSF_TASK_INTERFACES = {
            /* API v1 */
            "org.camunda.bpm.engine.delegate.JavaDelegate",
            /* API v2 */
            "dev.dsf.bpe.v2.activity.ServiceTask",
            "dev.dsf.bpe.v2.activity.MessageSendTask",
            "dev.dsf.bpe.v2.activity.MessageIntermediateThrowEvent",
            "dev.dsf.bpe.v2.activity.MessageEndEvent",
            "dev.dsf.bpe.v2.activity.UserTaskListener"
    };

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
     * Checks whether a class with the given fully-qualified name can be successfully loaded.
     * <p>
     * This method attempts to resolve the specified class name using multiple strategies, in order:
     * </p>
     * <ol>
     *   <li>Via the current thread's context class loader</li>
     *   <li>Via a custom {@link URLClassLoader} constructed from the given {@code projectRoot} directory,
     *       including common Maven/Gradle build paths and dependency JARs</li>
     *   <li>By directly checking the file system for a {@code .class} file corresponding to the class</li>
     * </ol>
     *
     * <p>
     * This layered approach ensures compatibility with typical local builds and CI environments using exploded plugin layouts.
     * If any strategy successfully resolves the class, the method returns {@code true}.
     * Otherwise, it logs diagnostic output and returns {@code false}.
     * </p>
     *
     * <p>
     * For file-based fallback resolution, the method checks the following locations:
     * </p>
     * <ul>
     *   <li>{@code projectRoot/com/example/MyClass.class}</li>
     *   <li>{@code projectRoot/target/classes/com/example/MyClass.class}</li>
     *   <li>{@code projectRoot/build/classes/com/example/MyClass.class}</li>
     * </ul>
     *
     * @param className   The fully-qualified class name to check (e.g., {@code com.example.MyClass})
     * @param projectRoot The root directory of the project or exploded JAR (used to construct custom class loader and file-based fallback)
     * @return {@code true} if the class is loadable through any of the available mechanisms; {@code false} otherwise
     *
     * @see ClassLoader
     * @see java.net.URLClassLoader
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
            // Fallback to custom loader
        }
        catch (NoClassDefFoundError err)
        {
            System.err.println("DEBUG: NoClassDefFoundError while trying context CL: " + err.getMessage());
        }
        catch (ExceptionInInitializerError err)
        {
            System.err.println("DEBUG: ExceptionInInitializerError while trying context CL: " + err.getMessage());
        }
        catch (UnsatisfiedLinkError err)
        {
            System.err.println("DEBUG: UnsatisfiedLinkError while trying context CL: " + err.getMessage());
        }
        catch (ClassFormatError err)
        {
            System.err.println("DEBUG: ClassFormatError while trying context CL: " + err.getMessage());
        }
        catch (Error err)
        {
            // Catching Error is generally discouraged, but logged here for class-loading edge cases
            System.err.println("DEBUG: Serious error while trying context CL: " + err.getMessage());
        }
        catch (Exception e)
        {
            // If some other runtime exception occurs
            System.err.println("DEBUG: Exception while trying context CL: " + e.getMessage());
        }

        // Second, try a custom class loader if projectRoot is provided
        if (projectRoot != null)
        {
            try
            {
                ClassLoader urlCl = createProjectClassLoader(projectRoot);
                urlCl.loadClass(className);
                return true;
            }
            catch (ClassNotFoundException e)
            {
                // No fallback beyond this
            }
            catch (NoClassDefFoundError err)
            {
                System.err.println("DEBUG: NoClassDefFoundError while custom loading " + className + ": " + err.getMessage());
            }
            catch (ExceptionInInitializerError err)
            {
                System.err.println("DEBUG: ExceptionInInitializerError while custom loading " + className + ": " + err.getMessage());
            }
            catch (UnsatisfiedLinkError err)
            {
                System.err.println("DEBUG: UnsatisfiedLinkError while custom loading " + className + ": " + err.getMessage());
            }
            catch (ClassFormatError err)
            {
                System.err.println("DEBUG: ClassFormatError while custom loading " + className + ": " + err.getMessage());
            }
            catch (Error err)
            {
                // Again, logged but not rethrown, to maintain boolean return
                System.err.println("DEBUG: Serious error while custom loading " + className + ": " + err.getMessage());
            }
            catch (Exception e)
            {
                System.err.println("DEBUG: Exception while custom loading " + className + ": " + e.getMessage());
            }
        }
        // File‐system fallback: look for the .class file directly
        if (projectRoot != null) {
            // Convert FQCN to relative path, e.g. "com.example.MyClass" → "com/example/MyClass.class"
            String relPath = className.replace('.', '/') + ".class";

            // a) Flat layout under projectRoot (e.g. CI: output/de/…/Listener.class)
            if (new File(projectRoot, relPath).exists())
                return true;

            // b) Maven convention: target/classes
            if (new File(projectRoot, "target/classes/" + relPath).exists())
                return true;

            // c) Gradle convention: build/classes
            return new File(projectRoot, "build/classes/" + relPath).exists();
        }
        return false;
    }

    // BpmnValidationUtils.java
    /**
     * Creates a {@link URLClassLoader} configured to load classes and resources from the project's
     * build outputs and embedded JARs.
     * <p>
     * This method attempts to construct a class loader with the following search paths, in order:
     * <ol>
     *   <li>{@code target/classes} – the default Maven output directory</li>
     *   <li>{@code build/classes} – the default Gradle output directory</li>
     *   <li>Project root directory – allows loading from exploded JARs or unpacked source trees</li>
     *   <li>All {@code *.jar} files located directly in the project root – fallback when JARs are manually placed</li>
     *   <li>{@code target/dependency/*.jar} – conventional Maven dependencies directory (e.g., from copy-dependencies)</li>
     * </ol>
     * This makes the method compatible with exploded plugin setups such as those found in CI pipelines,
     * where the classes reside directly in the root or inside a flat file structure.
     * </p>
     *
     * @param projectRoot the root directory of the exploded JAR or the Maven/Gradle project
     * @return a {@link URLClassLoader} that can load project classes and dependencies
     * @throws Exception if URL conversion or class loader initialization fails
     */
    private static ClassLoader createProjectClassLoader(File projectRoot) throws Exception
    {
        List<URL> urls = new ArrayList<>();

        // 1) allow loading exploded classes under the project root
        urls.add(projectRoot.toURI().toURL());

        // 2) classic Maven/Gradle output dirs
        File classesDir = new File(projectRoot, "target/classes");
        if (!classesDir.exists())
            classesDir = new File(projectRoot, "build/classes");
        if (classesDir.exists())
            urls.add(classesDir.toURI().toURL());

        // 3) include plugin.jar itself (so you don't even have to unzip it)
        File pluginJar = new File(projectRoot, "plugin.jar");
        if (pluginJar.isFile())
            urls.add(pluginJar.toURI().toURL());

        // 4) fall back on any other top-level JARs
        File[] rootJars = projectRoot.listFiles(f -> f.getName().toLowerCase().endsWith(".jar"));
        if (rootJars != null)
            for (File jar : rootJars)
                if (!jar.equals(pluginJar))
                    urls.add(jar.toURI().toURL());

        // 5) copied dependencies
        File depDir = new File(projectRoot, "target/dependency");
        if (depDir.isDirectory())
            for (File jar : Objects.requireNonNull(depDir.listFiles((d, n) -> n.endsWith(".jar"))))
                urls.add(jar.toURI().toURL());

        return new URLClassLoader(urls.toArray(new URL[0]),
                Thread.currentThread().getContextClassLoader());
    }
    /**
     * old
     * Checks if the class with the given name implements {@code org.camunda.bpm.engine.delegate.JavaDelegate}.
     * <p>
     * The check is performed by loading both the candidate class and the {@code JavaDelegate} interface using
     * a custom class loader, then verifying assignability.
     * </p>
     * new
     * Methode is not anymore used after the new version von DSF (API 2)
     * @param className   the fully-qualified class name to check
     * @param projectRoot the project root directory used to create the custom class loader
     * @return {@code true} if the candidate class implements {@code JavaDelegate}; {@code false} otherwise
     */
    @Deprecated
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
        if (isEmpty(implClass))
        {
            issues.add(new BpmnMessageSendEventImplementationClassEmptyValidationItem(elementId, bpmnFile, processId));
        }
        else if (!classExists(implClass, projectRoot))
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
        if (FhirValidator.activityDefinitionExists(messageName, projectRoot))
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
        if (FhirValidator.structureDefinitionExists(messageName, projectRoot))
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
                if (!BpmnValidationUtils.isEmpty(implClass))
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


    /**
     * Validates {@code <camunda:taskListener>} definitions on a {@link UserTask} element.
     *
     * <p>
     * This method performs the following validations for each {@code <camunda:taskListener>}:
     * </p>
     * <ul>
     *   <li><b>Missing class attribute:</b> If no {@code class} attribute is provided, a
     *       {@link BpmnUserTaskListenerMissingClassAttributeValidationItem} is added.</li>
     *   <li><b>Class existence:</b> If the class name is present but not found on the classpath, a
     *       {@link BpmnUserTaskListenerJavaClassNotFoundValidationItem} is added. If it exists, a success item is registered.</li>
     *   <li><b>API-specific inheritance/interface check:</b>
     *     <ul>
     *       <li>For API version {@code V1}, the listener class must either
     *           <b>extend</b> {@code dev.dsf.bpe.v1.activity.DefaultUserTaskListener}
     *           or <b>implement</b> {@code org.camunda.bpm.engine.delegate.TaskListener}.</li>
     *       <li>For API version {@code V2}, the listener class must either
     *           <b>extend</b> {@code dev.dsf.bpe.v2.activity.DefaultUserTaskListener}
     *           or <b>implement</b> {@code dev.dsf.bpe.v2.activity.UserTaskListener}.</li>
     *       <li>If neither requirement is met, a {@link BpmnUserTaskListenerNotExtendingOrImplementingRequiredClassValidationItem}
     *           is registered.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param userTask   the {@link UserTask} element to validate
     * @param elementId  the BPMN element ID
     * @param issues     the list of validation items to collect results
     * @param bpmnFile   the BPMN file being validated
     * @param processId  the ID of the BPMN process definition
     * @param projectRoot the root directory of the project, used for class loading
     */
    public static void checkTaskListenerClasses(
            UserTask userTask,
            String elementId,
            List<BpmnElementValidationItem> issues,
            File bpmnFile,
            String processId,
            File projectRoot)
    {
        if (userTask.getExtensionElements() == null) return;

        Collection<CamundaTaskListener> listeners = userTask.getExtensionElements()
                .getElementsQuery()
                .filterByType(CamundaTaskListener.class)
                .list();

        for (CamundaTaskListener listener : listeners)
        {
            String implClass = listener.getCamundaClass();

            // 1) Missing class attribute
            if (isEmpty(implClass))
            {
                issues.add(new BpmnUserTaskListenerMissingClassAttributeValidationItem(elementId, bpmnFile, processId));
                continue;
            }
            else
            {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "UserTask listener declares a class attribute: '" + implClass + "'"));
            }

            // 2) Class existence
            if (!classExists(implClass, projectRoot))
            {
                issues.add(new BpmnUserTaskListenerJavaClassNotFoundValidationItem(
                        elementId, bpmnFile, processId, implClass));
                continue;
            }
            else
            {
                issues.add(new BpmnElementValidationItemSuccess(
                        elementId, bpmnFile, processId,
                        "UserTask listener class '" + implClass + "' was found on the project classpath"));
            }

            // 3) API-specific checks with Either-Or logic
            ApiVersion apiVersion = ApiVersionHolder.getVersion();

            if (apiVersion == ApiVersion.UNKNOWN) {
            System.err.println("DEBUG: Unknown API version for UserTask listener validation: " + apiVersion + ". No specific checks applied.");
            }

            else if (apiVersion == ApiVersion.V2)
            {
                String defaultSuperClass = "dev.dsf.bpe.v2.activity.DefaultUserTaskListener";
                String requiredInterface = "dev.dsf.bpe.v2.activity.UserTaskListener";

                extendsDefault(elementId, issues, bpmnFile, processId, projectRoot, implClass, defaultSuperClass, requiredInterface);
            }
            else if (apiVersion == ApiVersion.V1)
            {
                String defaultSuperClass = "dev.dsf.bpe.v1.activity.DefaultUserTaskListener";
                String requiredInterface = "org.camunda.bpm.engine.delegate.TaskListener";

                extendsDefault(elementId, issues, bpmnFile, processId, projectRoot, implClass, defaultSuperClass, requiredInterface);
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
            else if (!isTimeCycleEmpty || !isTimeDurationEmpty)
            {
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
            if (!FhirValidator.structureDefinitionExists(literalValue, projectRoot))
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

    /**
     * Determines whether the specified class implements any of the DSF-supported task interfaces
     * listed in {@link #DSF_TASK_INTERFACES}.
     * <p>
     * This includes support for both DSF API v1 and API v2 interfaces, such as {@code JavaDelegate},
     * {@code ServiceTask}, {@code TaskMessageSend}, and others. The method attempts to load the target
     * class and each interface using a custom class loader scoped to the given project directory.
     * </p>
     *
     * @param className   the fully-qualified name of the class to inspect
     * @param projectRoot the root directory of the project (used to resolve class and dependency paths)
     * @return {@code true} if the class implements any known DSF task interface; {@code false} otherwise
     */
    public static boolean implementsDsfTaskInterface(String className, File projectRoot)
    {
        try
        {
            ClassLoader cl = createProjectClassLoader(projectRoot);
            Class<?> candidate = Class.forName(className, false, cl);

            for (String ifaceName : DSF_TASK_INTERFACES)
            {
                try
                {
                    Class<?> iface = Class.forName(ifaceName, false, cl);
                    if (iface.isAssignableFrom(candidate))
                        return true;
                }
                catch (ClassNotFoundException ignore) { /* interface not on class‑path */ }
            }
        }
        catch (Throwable t)
        {
            System.err.println("DEBUG: " + t.getMessage());
        }
        return false;
    }

    /**
     * Checks if the given class implements the given interface.
     *
     * @param className     fully-qualified name of the candidate class
     * @param interfaceName fully-qualified name of the required interface
     * @param projectRoot   project root used to assemble a class loader
     * @return true if {@code className} implements {@code interfaceName}, false otherwise
     */
    public static boolean implementsInterface(String className, String interfaceName, File projectRoot)
    {
        try
        {
            ClassLoader cl = createProjectClassLoader(projectRoot);
            Class<?> candidate = Class.forName(className, false, cl);
            Class<?> iface = Class.forName(interfaceName, false, cl);
            return iface.isAssignableFrom(candidate);
        }
        catch (Throwable t)
        {
            System.err.println("DEBUG: implementsInterface failed for " + className + " -> " + interfaceName + ": " + t.getMessage());
            return false;
        }
    }

    /**
     * Checks if the given class is a subclass (directly or indirectly) of the given superclass name.
     *
     * @param className       fully-qualified name of the candidate class
     * @param superClassName  fully-qualified name of the required superclass
     * @param projectRoot     project root used to assemble a class loader
     * @return true if {@code className} extends (directly or indirectly) {@code superClassName}, false otherwise
     */
    public static boolean isSubclassOf(String className, String superClassName, File projectRoot)
    {
        try
        {
            ClassLoader cl = createProjectClassLoader(projectRoot);
            Class<?> target = Class.forName(className, false, cl);
            Class<?> required = Class.forName(superClassName, false, cl);

            Class<?> current = target.getSuperclass();
            while (current != null)
            {
                if (current.equals(required))
                    return true;
                current = current.getSuperclass();
            }
        }
        catch (Throwable t)
        {
            System.err.println("DEBUG: isSubclassOf failed for " + className + " -> " + superClassName + ": " + t.getMessage());
        }
        return false;
    }
}
