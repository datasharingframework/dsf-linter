package dev.dsf.linter.classloading;

import dev.dsf.linter.logger.ConsoleLogger;
import dev.dsf.linter.logger.Logger;

import java.io.File;
import java.net.URLClassLoader;
import java.util.Optional;

import static dev.dsf.linter.classloading.ProjectClassLoaderFactory.getOrCreateProjectClassLoader;
import static dev.dsf.linter.util.validation.ValidationUtils.isEmpty;
import static dev.dsf.linter.constants.DsfApiConstants.DSF_TASK_INTERFACES;

public class ClassInspector {

    public static final Logger logger = new ConsoleLogger(false);


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
     *   <li>{@code projectRoot/build/classes/main/java/com/example/MyClass.class}</li>
     * </ul>
     *
     * @param className   The fully-qualified class name to check (e.g., {@code com.example.MyClass})
     * @param projectRoot The root directory of the project or exploded JAR (used to construct custom class loader and file-based fallback)
     * @return {@code true} if the class is loadable through any of the available mechanisms; {@code false} otherwise
     *
     * @see ClassLoader
     * @see URLClassLoader
     */
    public static boolean classExists(String className, File projectRoot) {
        // 0) Quick guard
        if (className == null || className.isBlank()) return false;

        // 1) Try the Thread Context ClassLoader (TCCL) first.
        try {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            if (tccl != null) {
                tccl.loadClass(className);        // does not trigger class initialization
                return true;
            }
        } catch (ClassNotFoundException ignore) {
            // Fall through to the next strategy.
        } catch (LinkageError | Exception ex) {
            // LinkageError covers NoClassDefFoundError, UnsatisfiedLinkError, ClassFormatError, etc.
            logger.debug("TCCL could not resolve " + className + ": " + ex);
        }

        // 2) Use a project-scoped URLClassLoader (covers exploded plugin + target/dependencies/**.jar)
        if (projectRoot != null) {
            try {
                ClassLoader urlCl = getOrCreateProjectClassLoader(projectRoot);
                urlCl.loadClass(className);
                return true;
            } catch (ClassNotFoundException ignore) {
                // Fall through to file-system heuristic.
            } catch (LinkageError | Exception ex) {
                logger.debug("Project CL could not resolve " + className + ": " + ex);
            }
        }

        // 3) File-system heuristic (cheap last resort for loose .class files)
        if (projectRoot != null) {
            String relPath = className.replace('.', '/') + ".class";

            // a) Flat under projectRoot (our CI exploded layout)
            if (new File(projectRoot, relPath).exists()) return true;

            // b) Maven: target/classes
            if (new File(projectRoot, "target/classes/" + relPath).exists()) return true;

            // c) Gradle: build/classes
            return new File(projectRoot, "build/classes/" + relPath).exists();
        }
        return false;
    }


    public static boolean implementsDsfTaskInterface(String className, File projectRoot) {
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            Optional<Class<?>> candidateClass = loadClass(className, cl);

            if (candidateClass.isEmpty()) {
                return false;
            }

            for (String ifaceName : DSF_TASK_INTERFACES) {
                Optional<Class<?>> ifaceClass = loadClass(ifaceName, cl);
                if (ifaceClass.isPresent() && ifaceClass.get().isAssignableFrom(candidateClass.get())) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to create project class loader for DSF task interface check: " + e.getMessage());
        }
        return false;
    }


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


    public static boolean isSubclassOf(String className, String superClassName, File projectRoot) {
        try {
            ClassLoader cl = getOrCreateProjectClassLoader(projectRoot);
            Optional<Class<?>> targetClass = loadClass(className, cl);
            Optional<Class<?>> requiredSuperclass = loadClass(superClassName, cl);

            if (targetClass.isEmpty() || requiredSuperclass.isEmpty()) {
                return false;
            }

            // isAssignableFrom also works for superclasses, which is cleaner than iterating.
            // A superclass is "assignable from" its subclass.
            return requiredSuperclass.get().isAssignableFrom(targetClass.get())
                    && !targetClass.get().equals(requiredSuperclass.get()); // Ensure it's a proper subclass

        } catch (Exception e) {
            logger.debug("Failed during isSubclassOf check for " + className + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads a class by its fully-qualified name using a project-specific class loader.
     * <p>
     * This helper method encapsulates the logic for retrieving the project's class loader
     * and loading a specific class, handling potential {@link ClassNotFoundException} or other
     * linkage errors gracefully by returning an {@link Optional}. This avoids cluttering
     * calling methods with repetitive try-catch blocks.
     * </p>
     *
     * @param className   The fully-qualified name of the class to load.
     * @param cl          The ClassLoader to use for loading the class.
     * @return An {@code Optional} containing the {@link Class} object if found; otherwise, an empty {@code Optional}.
     */
    private static Optional<Class<?>> loadClass(String className, ClassLoader cl) {
        if (isEmpty(className) || cl == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(className, false, cl));
        } catch (ClassNotFoundException | LinkageError e) {
            // LinkageError covers cases like NoClassDefFoundError.
            // Log the error for debugging purposes without stopping the validation.
            logger.debug("Could not load class '" + className + "': " + e.getMessage());
            return Optional.empty();
        }
    }

}
