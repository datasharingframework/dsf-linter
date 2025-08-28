package dev.dsf.utils.validator.util;

import dev.dsf.utils.validator.FloatingElementType;
import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.ValidationType;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.logger.ConsoleLogger;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.nio.file.DirectoryStream;
import java.net.URI;

/**
 * Provides a collection of static utility methods to support the validation of BPMN 2.0 process models,
 * with a special focus on Camunda extensions and DSF (Digital Service Framework) conventions.
 * <p>
 * This class is designed as a stateless utility and contains no public constructor. Its responsibilities
 * can be grouped into several key areas:
 * <ul>
 * <li><b>BPMN Element Validation:</b> Offers fine-grained checks for various BPMN elements, including
 * listeners (execution, task), events (timer, error, conditional), and custom field injections
 * like {@code profile} and {@code instantiatesCanonical}.</li>
 * <li><b>FHIR Resource Integration:</b> Includes methods to cross-reference BPMN artifacts (e.g., message names)
 * against corresponding FHIR {@code ActivityDefinition} and {@code StructureDefinition} resources.</li>
 * <li><b>Class & Classpath Management:</b> Provides a sophisticated, thread-safe, and cached mechanism
 * to create project-specific ClassLoaders for dynamic class validation.</li>
 * </ul>
 * </p>
 * <h3>Classpath Management</h3>
 * <p>
 * A core feature of this utility is its robust classpath management. It is designed to handle complex
 * project layouts, such as multi-module Maven/Gradle projects, and is optimized for performance in
 * local and CI/CD environments. Key capabilities include:
 * <ul>
 * <li><b>Standard and Recursive Loading:</b> Support for both flat and recursive directory scanning to
 * reliably find build artifacts.</li>
 * <li><b>Build System Awareness:</b> Automatic detection of common Maven, Gradle, and IntelliJ build
 * output directories via centralized path constants.</li>
 * <li><b>Performance:</b> Aggressive caching of {@link ClassLoader} instances minimizes I/O overhead,
 * and the use of {@link java.net.URI} prevents slow network lookups during path resolution.</li>
 * </ul>
 * </p>
 * <h3>Key Technologies & Specifications</h3>
 * <p>
 * <ul>
 * <li><a href="https://www.omg.org/spec/BPMN/2.0">BPMN 2.0 Specification</a></li>
 * <li><a href="https://docs.camunda.org/manual/latest/user-guide/process-engine/extension-elements/">Camunda Extension Elements</a></li>
 * <li><a href="https://hl7.org/fhir/structuredefinition.html">FHIR StructureDefinition</a></li>
 * <li><a href="https://hl7.org/fhir/activitydefinition.html">FHIR ActivityDefinition</a></li>
 * </ul>
 * </p>
 */
public class BpmnValidationUtils
{
    private static final Logger logger = new ConsoleLogger();

    /**
     * The standard relative path to Maven's primary build output directory.
     */
    private static final Path MAVEN_CLASSES_PATH = Paths.get("target", "classes");

    /**
     * The standard relative path to Gradle's primary build output directory for Java projects.
     */
    private static final Path GRADLE_CLASSES_PATH = Paths.get("build", "classes", "java", "main");

    /**
     * The standard relative path to IntelliJ IDEA's primary build output directory.
     */
    private static final Path INTELLIJ_CLASSES_PATH = Paths.get("out", "production", "classes");

    /**
     * The standard relative path to the Maven dependency plugin's output directory.
     */
    private static final Path MAVEN_DEPENDENCY_PATH = Paths.get("target", "dependency");

    /**
     * The common relative path for dependency JARs in CI/CD environments.
     */
    private static final Path MAVEN_DEPENDENCIES_PATH = Paths.get("target", "dependencies");

    /**
     * Cache for storing {@link ClassLoader} instances mapped to their corresponding project root paths.
     * <p>
     * This cache prevents the overhead of creating duplicate class loaders for the same project directory,
     * which is especially beneficial when validating multiple BPMN files within the same project.
     * The cache uses canonical file paths as keys to ensure consistent mapping even when different
     * {@link File} objects point to the same physical directory.
     * </p>
     *
     * @see #getOrCreateProjectClassLoader(File)
     * @see #createProjectClassLoader(File)
     */
    private static final ConcurrentMap<Path, ClassLoader> CL_CACHE = new ConcurrentHashMap<>();


    /**
     * Thread-safe cache for storing recursive {@link ClassLoader} instances mapped to their corresponding project root paths.
     * <p>
     * This cache prevents the overhead of creating duplicate recursive class loaders for the same project directory,
     * which is especially beneficial when validating multiple BPMN files within the same project or when working
     * with multi-module Maven/Gradle projects. The cache uses canonical file paths with a "#recursive" suffix as keys
     * to ensure consistent mapping and to distinguish from the standard project class loader cache.
     * </p>
     * <p>
     * Unlike the standard {@link #CL_CACHE}, this cache is specifically designed for recursive class loaders that
     * traverse the entire project structure, including nested modules and their build outputs. The cached class loaders
     * remain open during the validation session to avoid repeated JAR file operations and directory traversals.
     * </p>
     *
     * @see #getOrCreateRecursiveProjectClassLoader(File)
     * @see #createRecursiveProjectClassLoader(File)
     */
    private static final ConcurrentMap<Path, ClassLoader> CL_RECURSIVE_CACHE = new ConcurrentHashMap<>();

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

    // BpmnValidationUtils.java
    /**
     * Creates a {@link URLClassLoader} configured to load classes and resources from the project's
     * standard build outputs and dependency directories.
     * <p>
     * This method constructs a class loader by scanning for common build artifacts using predefined path
     * constants. The search paths include:
     * <ul>
     * <li>The project root directory itself (for exploded layouts).</li>
     * <li>Standard build output directories for Maven ({@code target/classes}) and Gradle
     * ({@code build/classes/java/main}), defined by {@link #MAVEN_CLASSES_PATH} and {@link #GRADLE_CLASSES_PATH}.</li>
     * <li>JAR files located directly in the project root.</li>
     * <li>Conventional Maven dependency directories, such as {@code target/dependency} and
     * {@code target/dependencies}, defined by {@link #MAVEN_DEPENDENCY_PATH} and {@link #MAVEN_DEPENDENCIES_PATH}.</li>
     * </ul>
     * The parent class loader is set to the current thread's context class loader, which is crucial
     * for compatibility with containerized environments like Docker.
     *
     * @param projectRoot the root directory of the exploded JAR or the Maven/Gradle project.
     * @return a {@link URLClassLoader} that can load project classes and dependencies.
     * @throws Exception if URL conversion or class loader initialization fails.
     * @see #MAVEN_CLASSES_PATH
     * @see #GRADLE_CLASSES_PATH
     * @see #MAVEN_DEPENDENCY_PATH
     */
    public static ClassLoader createProjectClassLoader(File projectRoot) throws Exception {
        List<URL> urls = new ArrayList<>();
        Path rootPath = projectRoot.toPath();

        // 0) Root (so loose classes like output/com/... are visible)
        urls.add(rootPath.toUri().toURL());

        // 1) Typical build output dirs using constants
        Path mavenClasses = rootPath.resolve(MAVEN_CLASSES_PATH);
        if (Files.isDirectory(mavenClasses)) {
            urls.add(mavenClasses.toUri().toURL());
        }

        Path gradleClasses = rootPath.resolve(GRADLE_CLASSES_PATH);
        if (Files.isDirectory(gradleClasses)) {
            urls.add(gradleClasses.toUri().toURL());
        }

        // 2) JARs sitting in the project root (e.g., plugin.jar)
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rootPath, "*.jar")) {
            for (Path jar : ds) {
                urls.add(jar.toUri().toURL());
            }
        }

        // 3) Classic maven-dependency-plugin output using constants
        Path legacyDeps = rootPath.resolve(MAVEN_DEPENDENCY_PATH);
        if (Files.isDirectory(legacyDeps)) {
            try (Stream<Path> s = Files.list(legacyDeps)) {
                s.filter(p -> p.toString().endsWith(".jar")).forEach(p -> {
                    try {
                        urls.add(p.toUri().toURL());
                    } catch (Exception ignore) {
                    }
                });
            }
        }

        // 4) CI repository layout using constants
        Path depsRoot = rootPath.resolve(MAVEN_DEPENDENCIES_PATH);
        if (Files.isDirectory(depsRoot)) {
            try (Stream<Path> s = Files.walk(depsRoot)) {
                s.filter(p -> p.toString().endsWith(".jar")).forEach(p -> {
                    try {
                        urls.add(p.toUri().toURL());
                    } catch (Exception ignore) {
                    }
                });
            }
        }

        return new URLClassLoader(urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
    }


    /**
     * Creates a recursive {@link URLClassLoader} that scans the entire project hierarchy for build
     * outputs and dependency JARs.
     * <p>
     * This method performs a comprehensive traversal of the project structure. It is particularly
     * useful for complex multi-module projects where build artifacts are located in various subdirectories.
     * The search logic uses predefined path constants for maintainability.
     * </p>
     * <p>
     * To avoid performance issues with network lookups caused by {@link URL#equals(Object)}, this
     * implementation uses a {@link Set} of {@link URI} objects for collecting paths and converts
     * them to URLs only at the final step.
     * </p>
     *
     * @param projectRoot the root directory of the project or multi-module structure to traverse.
     * @return a {@link URLClassLoader} that can load classes from the entire project hierarchy.
     * @throws Exception if URI/URL conversion, directory traversal, or class loader initialization fails.
     * @see #getOrCreateRecursiveProjectClassLoader(File)
     */
    public static ClassLoader createRecursiveProjectClassLoader(File projectRoot) throws Exception {
        final Path root = projectRoot.toPath();
        // Change: Use a Set<URI> to avoid potential network I/O from URL.equals()
        final Set<URI> uris = new LinkedHashSet<>();

        // Always include root so "exploded" class dirs are visible
        uris.add(projectRoot.toURI());

        // Recursively add common build output directories using constants
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isDirectory)
                    .filter(p -> p.endsWith(MAVEN_CLASSES_PATH)
                            || p.endsWith(GRADLE_CLASSES_PATH)
                            || p.endsWith(INTELLIJ_CLASSES_PATH))
                    .forEach(p -> uris.add(p.toUri()));
        }

        // Recursively add dependency jars using constants
        final String targetDepStr = File.separator + MAVEN_DEPENDENCY_PATH + File.separator;
        final String targetDepsStr = File.separator + MAVEN_DEPENDENCIES_PATH + File.separator;
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String ps = p.toString();
                        return (ps.contains(targetDepStr) || ps.contains(targetDepsStr)) && ps.endsWith(".jar");
                    })
                    .forEach(p -> uris.add(p.toUri()));
        }

        // Add root-level *.jar files at the top-level project root
        try (Stream<Path> s = Files.walk(root, 1)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .forEach(p -> uris.add(p.toUri()));
        }

        // Convert the safe URI set to a URL array only when creating the ClassLoader
        URL[] urls = uris.stream().map(uri -> {
            try {
                return uri.toURL();
            } catch (Exception e) {
                // This is unlikely to happen for file-based URIs, but handle it defensively
                throw new RuntimeException("Failed to convert URI to URL: " + uri, e);
            }
        }).toArray(URL[]::new);

        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Retrieves or creates a cached recursive {@link ClassLoader} for the specified project root.
     * <p>
     * This method provides a thread-safe, cached accessor for a recursive class loader, ideal for complex,
     * multi-module projects. It prevents the performance overhead of repeatedly scanning the entire project
     * directory structure by storing the created {@link ClassLoader} in {@link #CL_RECURSIVE_CACHE}.
     * The cache key is derived from the canonical path of the project root, suffixed with "#recursive"
     * to avoid collisions with the standard class loader cache.
     * </p>
     * <p>
     * The underlying creation logic is delegated to {@link #createRecursiveProjectClassLoader(File)}, which
     * performs a deep scan for all build outputs and dependency JARs. The entire caching mechanism is
     * handled by the generic {@link #getOrCreateCachedClassLoader(Path, ConcurrentMap, Supplier)} helper method.
     * </p>
     *
     * <p><b>Use Cases:</b></p>
     * <ul>
     * <li>Validating BPMN files in multi-module Maven or Gradle projects.</li>
     * <li>Resolving classes across different sub-project classpaths.</li>
     * <li>CI/CD environments where the same complex project is validated multiple times.</li>
     * </ul>
     *
     * @param projectRoot The root directory of the project structure to be scanned recursively.
     * @return A cached or newly created recursive {@link ClassLoader}. The instance is shared across threads.
     * @throws RuntimeException Wraps any {@link Exception} thrown during the class loader's creation,
     * providing context about the failed operation.
     *
     * @see #createRecursiveProjectClassLoader(File)
     * @see #getOrCreateCachedClassLoader(Path, ConcurrentMap, Supplier)
     * @see #CL_RECURSIVE_CACHE
     */
    public static ClassLoader getOrCreateRecursiveProjectClassLoader(File projectRoot) throws Exception {
        Path key = projectRoot.getCanonicalFile().toPath().resolve("#recursive");
        return getOrCreateCachedClassLoader(key, CL_RECURSIVE_CACHE, () -> {
            try {
                return createRecursiveProjectClassLoader(projectRoot);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Retrieves or creates a cached standard {@link ClassLoader} for the specified project root.
     * <p>
     * This method provides thread-safe, cached access to a standard, non-recursive class loader. It is designed
     * for single-module projects or scenarios where a deep, recursive scan is not needed. The instance is
     * stored in {@link #CL_CACHE} to avoid the cost of recreating it on subsequent calls for the same project.
     * The cache key is the canonical path of the project root.
     * </p>
     * <p>
     * The creation of the class loader, which includes standard build paths like {@code target/classes}
     * and {@code target/dependency}, is handled by {@link #createProjectClassLoader(File)}. This method
     * uses the generic {@link #getOrCreateCachedClassLoader(Path, ConcurrentMap, Supplier)} to abstract
     * the caching logic.
     * </p>
     *
     * <p><b>Use Cases:</b></p>
     * <ul>
     * <li>Validating BPMN files within a simple, flat project structure.</li>
     * <li>Frequent class existence checks in a single-module context.</li>
     * <li>Improving performance by avoiding repeated I/O operations on JARs.</li>
     * </ul>
     *
     * @param projectRoot The root directory of the project.
     * @return A cached or newly created standard {@link ClassLoader}. The instance is shared across threads.
     * @throws RuntimeException Wraps any {@link Exception} that occurs during class loader creation.
     *
     * @see #createProjectClassLoader(File)
     * @see #getOrCreateCachedClassLoader(Path, ConcurrentMap, Supplier)
     * @see #CL_CACHE
     */
    static ClassLoader getOrCreateProjectClassLoader(File projectRoot) throws Exception {
        Path key = projectRoot.getCanonicalFile().toPath();
        return getOrCreateCachedClassLoader(key, CL_CACHE, () -> {
            try {
                return createProjectClassLoader(projectRoot);
            } catch (Exception e) {
                // This is necessary because the lambda can't throw a checked exception directly.
                // The helper method will catch and re-throw it as a RuntimeException.
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A generic helper to retrieve or create a cached {@link ClassLoader}.
     * <p>
     * This private utility method implements a generic, thread-safe caching pattern using
     * {@link ConcurrentMap#computeIfAbsent(Object, java.util.function.Function)}. It atomically
     * checks for the existence of a key in the cache. If the key is absent, it executes the provided
     * {@code creator} {@link Supplier} to generate the value, stores it in the cache, and returns it.
     * </p>
     * <p>
     * Any checked {@link Exception} thrown by the {@code creator} is caught and wrapped in a
     * {@link RuntimeException}. This is necessary because the functional interface used by
     * {@code computeIfAbsent} does not allow throwing checked exceptions.
     * </p>
     *
     * @param key     The unique key to identify the entry in the cache.
     * @param cache   The {@link ConcurrentMap} instance serving as the cache.
     * @param creator A {@link Supplier} function that produces the {@link ClassLoader} instance if it is not found in the cache.
     * @return The cached or newly created {@link ClassLoader}.
     * @throws RuntimeException if the {@code creator} function fails, wrapping the original cause.
     */
    private static ClassLoader getOrCreateCachedClassLoader(
            Path key,
            ConcurrentMap<Path, ClassLoader> cache,
            Supplier<ClassLoader> creator) {

        return cache.computeIfAbsent(key, k -> {
            try {
                return creator.get();
            } catch (Exception e) {
                // Wrap checked exceptions from the creator function
                throw new RuntimeException("Failed to create and cache the classloader for key: " + k, e);
            }
        });
    }

    /**
     * Extracts the implementation class specified on a BPMN element.
     * <p>
     * The method first checks the element's direct "camunda:class" attribute. If not found,
     * it then inspects nested {@link MessageEventDefinition}s for elements like {@link ThrowEvent}
     * or {@link EndEvent} to find a "camunda:class" attribute there.
     * </p>
     *
     * @param element The BPMN {@link BaseElement} from which to extract the implementation class.
     * @return The implementation class as a string, or an empty string if not found.
     */
    public static String extractImplementationClass(BaseElement element) {
        // 1. Check for a direct "camunda:class" attribute on the element itself.
        String implClass = element.getAttributeValueNs("http://camunda.org/schema/1.0/bpmn", "class");
        if (!isEmpty(implClass)) {
            return implClass;
        }

        // 2. If not found, check for a class within nested event definitions.
        // IMPORTANT: Always check from the most specific to the most general type.
        Collection<EventDefinition> definitions = null;
        if (element instanceof EndEvent endEvent) { // FIRST, check for the more specific EndEvent
            definitions = endEvent.getEventDefinitions();
        } else if (element instanceof ThrowEvent throwEvent) { // THEN, check for the more general ThrowEvent
            definitions = throwEvent.getEventDefinitions();
        }

        // 3. Use the helper to extract the class from the definitions.
        return getCamundaClassFromMessageEvents(definitions).orElse("");
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
     * <p>
     * This method iterates through each {@code <camunda:taskListener>} associated with the user task
     * and performs a sequential, multi-step validation. If a critical validation fails (e.g., the
     * class attribute is missing or the class is not found), subsequent checks for that specific
     * listener are skipped to prevent cascading errors.
     * </p>
     * <p>The validation process includes the following checks in order:</p>
     * <ol>
     * <li><b>Class Attribute Presence:</b> Ensures the {@code camunda:class} attribute is specified and not empty.</li>
     * <li><b>Class Existence:</b> Verifies that the specified listener class exists on the project's classpath
     * using the {@link #classExists(String, File)} method.</li>
     * <li><b>API-Specific Inheritance:</b> Based on the active API version (V1 or V2), it determines the
     * required base class and interface. It then validates that the listener class either extends the
     * default base class or implements the required interface, using the {@link #extendsDefault} helper.</li>
     * </ol>
     *
     * @param userTask    the {@link UserTask} element to validate.
     * @param elementId   the BPMN element ID of the user task.
     * @param issues      the list of validation items where results are collected.
     * @param bpmnFile    the BPMN file being validated.
     * @param processId   the ID of the BPMN process definition.
     * @param projectRoot the root directory of the project, used for class loading.
     */
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
     * Determines whether a class implements any of the supported DSF (Digital Service Framework) task interfaces.
     * <p>
     * This method checks the given {@code className} against a predefined list of interfaces, including
     * those from DSF API v1 (e.g., {@code org.camunda.bpm.engine.delegate.JavaDelegate}) and v2 (e.g., {@code dev.dsf.bpe.v2.activity.ServiceTask}).
     * </p>
     * <p>
     * It operates efficiently by using a cached {@link ClassLoader} obtained via {@link #getOrCreateProjectClassLoader(File)},
     * preventing redundant classpath scanning. The actual class loading is delegated to the robust
     * {@link #loadClass(String, ClassLoader)} helper, which gracefully handles classes that are not found
     * or cannot be loaded by returning an {@link java.util.Optional}.
     * </p>
     *
     * @param className   The fully-qualified name of the class to be inspected.
     * @param projectRoot The root directory of the project, used to configure the class loader.
     * @return {@code true} if the class implements at least one of the supported DSF task interfaces, {@code false} otherwise.
     */
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

    /**
     * Checks if a given class implements a specific interface.
     * <p>
     * This method efficiently verifies the inheritance relationship by using a cached, project-specific
     * {@link ClassLoader} retrieved via {@link #getOrCreateProjectClassLoader(File)}. Both the candidate
     * class and the interface are loaded using the {@link #loadClass(String, ClassLoader)} helper,
     * which safely handles potential class loading failures by returning an {@link java.util.Optional}.
     * </p>
     *
     * @param className     The fully-qualified name of the class to check.
     * @param interfaceName The fully-qualified name of the interface that should be implemented.
     * @param projectRoot   The project's root directory, used for class loading.
     * @return {@code true} if the class successfully implements the specified interface, {@code false} otherwise,
     * including cases where either class or interface cannot be loaded.
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
     * Checks if a given class is a subclass of a specified superclass.
     * <p>
     * This method determines if {@code className} inherits from {@code superClassName}, either directly or
     * indirectly. It leverages the efficient {@link Class#isAssignableFrom(Class)} check, which is more
     * performant than iterating through the class hierarchy manually.
     * </p>
     * <p>
     * For class resolution, it relies on a cached {@link ClassLoader} from {@link #getOrCreateProjectClassLoader(File)}
     * and the safe {@link #loadClass(String, ClassLoader)} helper method to prevent errors if classes are not found.
     * The method also ensures that a class is not considered a subclass of itself.
     * </p>
     *
     * @param className      The fully-qualified name of the potential subclass.
     * @param superClassName The fully-qualified name of the required superclass.
     * @param projectRoot    The project's root directory, used to configure the class loader.
     * @return {@code true} if the class is a proper subclass of the superclass, {@code false} otherwise.
     */
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

    /**
     * Scans a collection of event definitions to find the Camunda class from a MessageEventDefinition.
     *
     * @param definitions The collection of {@link EventDefinition}s to search through.
     * @return A {@link java.util.Optional} containing the class name if found, otherwise an empty Optional.
     */
    private static Optional<String> getCamundaClassFromMessageEvents(Collection<EventDefinition> definitions) {
        if (definitions == null) {
            return Optional.empty();
        }

        return definitions.stream()
                .filter(MessageEventDefinition.class::isInstance)
                .map(MessageEventDefinition.class::cast)
                .map(MessageEventDefinition::getCamundaClass)
                .filter(className -> !isEmpty(className))
                .findFirst();
    }
}