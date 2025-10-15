package dev.dsf.linter.classloading;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ProjectClassLoaderFactory {

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
    public static ClassLoader getOrCreateProjectClassLoader(File projectRoot) throws Exception {
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

}
