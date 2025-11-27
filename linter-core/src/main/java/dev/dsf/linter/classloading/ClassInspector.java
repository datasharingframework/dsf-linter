package dev.dsf.linter.classloading;

import dev.dsf.linter.constants.BpmnElementType;
import dev.dsf.linter.constants.DsfApiConstants;
import dev.dsf.linter.logger.ConsoleLogger;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.util.api.ApiVersion;

import java.io.File;
import java.util.Optional;

import static dev.dsf.linter.classloading.ProjectClassLoaderFactory.getOrCreateProjectClassLoader;
import static dev.dsf.linter.constants.DsfApiConstants.*;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Inspects classes for DSF API compliance with proper version AND element-type isolation.
 *
 * <p>This class validates that implementation classes conform to the correct
 * DSF BPE API version (v1 or v2) AND implement the correct interface for
 * their specific BPMN element type.</p>
 *
 * <h3>Validation Hierarchy:</h3>
 * <ol>
 *   <li><b>Element-specific</b>: ServiceTask must implement ServiceTask interface</li>
 *   <li><b>Version-specific</b>: V1 uses JavaDelegate, V2 uses specific interfaces</li>
 *   <li><b>General</b>: Fallback when element type is unknown</li>
 * </ol>
 */
public class ClassInspector {

    public static final Logger logger = new ConsoleLogger(false);

    // ==================== CLASS EXISTENCE CHECK ====================

    /**
     * Checks whether a class with the given fully-qualified name can be loaded.
     *
     * @param className   The fully-qualified class name to check
     * @param projectRoot The root directory of the project
     * @return {@code true} if the class is loadable; {@code false} otherwise
     */
    public static boolean classExists(String className, File projectRoot) {
        if (className == null || className.isBlank()) return false;

        // 1) Try Thread Context ClassLoader
        try {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            if (tccl != null) {
                tccl.loadClass(className);
                return true;
            }
        } catch (ClassNotFoundException ignore) {
        } catch (LinkageError | Exception ex) {
            logger.debug("TCCL could not resolve " + className + ": " + ex);
        }

        // 2) Try project-scoped URLClassLoader
        if (projectRoot != null) {
            try {
                ClassLoader urlCl = getOrCreateProjectClassLoader(projectRoot);
                urlCl.loadClass(className);
                return true;
            } catch (ClassNotFoundException ignore) {
            } catch (LinkageError | Exception ex) {
                logger.debug("Project CL could not resolve " + className + ": " + ex);
            }
        }

        // 3) File-system heuristic
        if (projectRoot != null) {
            String relPath = className.replace('.', '/') + ".class";
            if (new File(projectRoot, relPath).exists()) return true;
            if (new File(projectRoot, "target/classes/" + relPath).exists()) return true;
            return new File(projectRoot, "build/classes/" + relPath).exists();
        }
        return false;
    }

    // ==================== ELEMENT-SPECIFIC VALIDATION (RECOMMENDED) ====================

    /**
     * Validates that a class implements the correct interface for a specific
     * BPMN element type AND API version.
     *
     * <p>This is the <b>recommended</b> method for validation as it enforces
     * both version isolation AND element-specific interface requirements.</p>
     *
     * @param className   The class to validate
     * @param projectRoot The project root directory
     * @param apiVersion  The API version (V1 or V2)
     * @param elementType The specific BPMN element type
     * @return {@code true} if the class implements the correct interface
     */
    public static boolean implementsCorrectInterface(
            String className,
            File projectRoot,
            ApiVersion apiVersion,
            BpmnElementType elementType) {

        String[] interfaces = getInterfacesForElement(apiVersion, elementType);
        return implementsAnyInterface(className, projectRoot, interfaces);
    }

    /**
     * Checks if a class does NOT implement the correct interface for a specific
     * BPMN element type AND API version.
     *
     * <p>This method is provided to avoid inverted boolean logic in calling code.
     * Use this method when checking for validation failures.</p>
     *
     * @param className   The class to validate
     * @param projectRoot The project root directory
     * @param apiVersion  The API version (V1 or V2)
     * @param elementType The specific BPMN element type
     * @return {@code true} if the class does NOT implement the correct interface
     */
    public static boolean doesNotImplementCorrectInterface(
            String className,
            File projectRoot,
            ApiVersion apiVersion,
            BpmnElementType elementType) {

        return !implementsCorrectInterface(className, projectRoot, apiVersion, elementType);
    }

    /**
     * Returns the expected interfaces for a specific element type and API version.
     *
     * <p>This method enforces strict element-specific interface requirements:</p>
     * <ul>
     *   <li>V1 ServiceTask/SendTask/Events → JavaDelegate only</li>
     *   <li>V1 UserTask Listener → TaskListener only</li>
     *   <li>V1 Execution Listener → ExecutionListener only</li>
     *   <li>V2 elements → Their specific V2 interfaces</li>
     * </ul>
     *
     * @param apiVersion  The API version (must be V1 or V2, not UNKNOWN)
     * @param elementType The BPMN element type
     * @return Array of expected interface names
     * @throws IllegalStateException if apiVersion is UNKNOWN (should never happen)
     */
    public static String[] getInterfacesForElement(ApiVersion apiVersion, BpmnElementType elementType) {
        // V1: Element-specific interface requirements
        if (apiVersion == ApiVersion.V1) {
            return getInterface(elementType, DsfApiConstants.V1_SERVICE_TASK_INTERFACES, DsfApiConstants.V1_SEND_TASK_INTERFACES, DsfApiConstants.V1_INTERMEDIATE_THROW_INTERFACES, DsfApiConstants.V1_END_EVENT_INTERFACES, DsfApiConstants.V1_TASK_LISTENER_INTERFACES, DsfApiConstants.V1_EXECUTION_LISTENER_INTERFACES, DsfApiConstants.V1_INTERFACES);
        }

        // V2: Element-specific interfaces
        if (apiVersion == ApiVersion.V2) {
            return getInterface(elementType, V2_SERVICE_TASK_INTERFACES, V2_SEND_TASK_INTERFACES, V2_INTERMEDIATE_THROW_INTERFACES, V2_END_EVENT_INTERFACES, V2_USER_TASK_LISTENER_INTERFACES, V2_EXECUTION_LISTENER_INTERFACES, V2_INTERFACES);
        }

        // UNKNOWN: This should never happen as API version is validated earlier
        return null;
    }

    private static String[] getInterface(BpmnElementType elementType, String[] v2ServiceTaskInterfaces, String[] v2SendTaskInterfaces, String[] v2IntermediateThrowInterfaces, String[] v2EndEventInterfaces, String[] v2UserTaskListenerInterfaces, String[] v2ExecutionListenerInterfaces, String[] v2Interfaces) {
        return switch (elementType) {
            case SERVICE_TASK -> v2ServiceTaskInterfaces;
            case SEND_TASK -> v2SendTaskInterfaces;
            case MESSAGE_INTERMEDIATE_THROW_EVENT -> v2IntermediateThrowInterfaces;
            case MESSAGE_END_EVENT -> v2EndEventInterfaces;
            case USER_TASK_LISTENER -> v2UserTaskListenerInterfaces;
            case EXECUTION_LISTENER -> v2ExecutionListenerInterfaces;
            case RECEIVE_TASK, GENERIC -> v2Interfaces; // General fallback
        };
    }

    /**
     * Returns a human-readable description of expected interfaces for a specific element.
     *
     * @param apiVersion  The API version (must be V1 or V2, not UNKNOWN)
     * @param elementType The BPMN element type
     * @return Formatted string for error messages
     * @throws IllegalStateException if apiVersion is UNKNOWN (should never happen)
     */
    public static String getExpectedInterfaceDescription(ApiVersion apiVersion, BpmnElementType elementType) {
        if (apiVersion == ApiVersion.V1) {
            return switch (elementType) {
                case SERVICE_TASK -> "JavaDelegate";
                case SEND_TASK -> "JavaDelegate";
                case MESSAGE_INTERMEDIATE_THROW_EVENT -> "JavaDelegate";
                case MESSAGE_END_EVENT -> "JavaDelegate";
                case USER_TASK_LISTENER -> "TaskListener";
                case EXECUTION_LISTENER -> "ExecutionListener (Camunda)";
                case RECEIVE_TASK, GENERIC -> "JavaDelegate, TaskListener, or ExecutionListener";
            };
        }

        if (apiVersion == ApiVersion.V2) {
            return switch (elementType) {
                case SERVICE_TASK -> "ServiceTask";
                case SEND_TASK -> "MessageSendTask";
                case MESSAGE_INTERMEDIATE_THROW_EVENT -> "MessageIntermediateThrowEvent";
                case MESSAGE_END_EVENT -> "MessageEndEvent";
                case USER_TASK_LISTENER -> "UserTaskListener";
                case EXECUTION_LISTENER -> "ExecutionListener (DSF V2)";
                case RECEIVE_TASK, GENERIC -> "a supported DSF V2 activity interface";
            };
        }

        // UNKNOWN: This should never happen as API version is validated earlier
        return null;
    }

    // ==================== INTERFACE DISCOVERY ====================

    /**
     * Finds which specific DSF interface a class implements.
     *
     * @param className   The class to check
     * @param projectRoot The project root directory
     * @param apiVersion  The API version context
     * @param elementType The BPMN element type
     * @return The implemented interface name, or {@code null} if none found
     */
    public static String findImplementedInterface(
            String className,
            File projectRoot,
            ApiVersion apiVersion,
            BpmnElementType elementType) {

        String[] interfaces = getInterfacesForElement(apiVersion, elementType);
        return findFirstImplementedInterface(className, projectRoot, interfaces);
    }


    // ==================== INHERITANCE CHECKS ====================

    /**
     * Checks if a class implements a specific interface.
     */
    public static boolean implementsInterface(String className, String interfaceName, File projectRoot) {
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            Optional<Class<?>> candidateClass = loadClass(className, cl);
            Optional<Class<?>> interfaceClass = loadClass(interfaceName, cl);

            return candidateClass.isPresent()
                    && interfaceClass.isPresent()
                    && interfaceClass.get().isAssignableFrom(candidateClass.get());
        } catch (Exception e) {
            logger.debug("Failed during implementsInterface check for " + className + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a class extends a specific superclass.
     */
    public static boolean isSubclassOf(String className, String superClassName, File projectRoot) {
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            Optional<Class<?>> targetClass = loadClass(className, cl);
            Optional<Class<?>> requiredSuperclass = loadClass(superClassName, cl);

            if (targetClass.isEmpty() || requiredSuperclass.isEmpty()) {
                return false;
            }

            return requiredSuperclass.get().isAssignableFrom(targetClass.get())
                    && !targetClass.get().equals(requiredSuperclass.get());
        } catch (Exception e) {
            logger.debug("Failed during isSubclassOf check for " + className + ": " + e.getMessage());
            return false;
        }
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Checks if a class implements any of the given interfaces.
     */
    private static boolean implementsAnyInterface(String className, File projectRoot, String[] interfaces) {
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            Optional<Class<?>> candidateClass = loadClass(className, cl);

            if (candidateClass.isEmpty()) {
                return false;
            }

            for (String ifaceName : interfaces) {
                Optional<Class<?>> ifaceClass = loadClass(ifaceName, cl);
                if (ifaceClass.isPresent() && ifaceClass.get().isAssignableFrom(candidateClass.get())) {
                    logger.debug("Class '" + className + "' implements " + ifaceName);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to check interfaces for " + className + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Finds the first interface from the array that the class implements.
     */
    private static String findFirstImplementedInterface(String className, File projectRoot, String[] interfaces) {
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            Optional<Class<?>> candidateClass = loadClass(className, cl);

            if (candidateClass.isEmpty()) {
                return null;
            }

            for (String ifaceName : interfaces) {
                Optional<Class<?>> ifaceClass = loadClass(ifaceName, cl);
                if (ifaceClass.isPresent() && ifaceClass.get().isAssignableFrom(candidateClass.get())) {
                    return ifaceName;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to find interface for " + className + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Loads a class by name using the provided ClassLoader.
     */
    private static Optional<Class<?>> loadClass(String className, ClassLoader cl) {
        if (isEmpty(className) || cl == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(className, false, cl));
        } catch (ClassNotFoundException | LinkageError e) {
            logger.debug("Could not load class '" + className + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts simple class name from fully qualified name.
     */
    public static String getSimpleName(String fqn) {
        if (fqn == null) return "unknown";
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }
}