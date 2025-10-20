package dev.dsf.linter.classloading;

import dev.dsf.linter.util.cache.ConcurrentCache;

import java.io.File;
import java.io.IOException;
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
import java.util.stream.Stream;

public class ProjectClassLoaderFactory {

    private static final Path MAVEN_CLASSES_PATH = Paths.get("target", "classes");
    private static final Path GRADLE_CLASSES_PATH = Paths.get("build", "classes", "java", "main");
    private static final Path INTELLIJ_CLASSES_PATH = Paths.get("out", "production", "classes");
    private static final Path MAVEN_DEPENDENCY_PATH = Paths.get("target", "dependency");
    private static final Path MAVEN_DEPENDENCIES_PATH = Paths.get("target", "dependencies");

    /**
     * Cache for standard project class loaders.
     * Uses cleanup callback to close URLClassLoaders when cache is cleared.
     */
    private static final ConcurrentCache<Path, ClassLoader> CL_CACHE = new ConcurrentCache<>(classLoader -> {
        if (classLoader instanceof URLClassLoader) {
            try {
                ((URLClassLoader) classLoader).close();
            } catch (IOException e) {
                // Best effort cleanup
            }
        }
    });

    /**
     * Cache for recursive project class loaders.
     * Uses cleanup callback to close URLClassLoaders when cache is cleared.
     */
    private static final ConcurrentCache<Path, ClassLoader> CL_RECURSIVE_CACHE = new ConcurrentCache<>(classLoader -> {
        if (classLoader instanceof URLClassLoader) {
            try {
                ((URLClassLoader) classLoader).close();
            } catch (IOException e) {
                // Best effort cleanup
            }
        }
    });

    /**
     * Retrieves or creates a cached standard {@link ClassLoader} for the specified project root.
     *
     * <p>This method provides thread-safe, cached access to a standard, non-recursive class loader.
     * The instance is stored in {@link #CL_CACHE} to avoid the cost of recreating it on subsequent calls
     * for the same project. The cache key is the canonical path of the project root.</p>
     *
     * @param projectRoot the root directory of the project
     * @return a cached or newly created standard {@link ClassLoader}
     * @throws RuntimeException if class loader creation or path resolution fails
     */
    public static ClassLoader getOrCreateProjectClassLoader(File projectRoot) {
        try {
            Path key = projectRoot.getCanonicalFile().toPath();
            return CL_CACHE.getOrCreate(key, k -> {
                try {
                    return createProjectClassLoader(projectRoot);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create project classloader for: " + projectRoot, e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve canonical path for: " + projectRoot, e);
        }
    }

    /**
     * Retrieves or creates a cached recursive {@link ClassLoader} for the specified project root.
     *
     * <p>This method provides thread-safe, cached access to a recursive class loader that scans
     * the entire project hierarchy. The instance is stored in {@link #CL_RECURSIVE_CACHE}.</p>
     *
     * @param projectRoot the root directory of the project structure to be scanned recursively
     * @return a cached or newly created recursive {@link ClassLoader}
     * @throws Exception if class loader creation or path resolution fails
     */
    public static ClassLoader getOrCreateRecursiveProjectClassLoader(File projectRoot) throws Exception {
        Path key = projectRoot.getCanonicalFile().toPath().resolve("#recursive");
        return CL_RECURSIVE_CACHE.getOrCreate(key, k -> {
            try {
                return createRecursiveProjectClassLoader(projectRoot);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create recursive classloader for: " + projectRoot, e);
            }
        });
    }

    /**
     * Clears both standard and recursive class loader caches.
     * This closes all cached URLClassLoaders to release resources.
     */
    public static void clearCaches() {
        CL_CACHE.clear();
        CL_RECURSIVE_CACHE.clear();
    }

    /**
     * Creates a {@link URLClassLoader} configured to load classes and resources from the project's
     * standard build outputs and dependency directories.
     *
     * @param projectRoot the root directory of the project
     * @return a {@link URLClassLoader} that can load project classes and dependencies
     * @throws Exception if URL conversion or class loader initialization fails
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
     * Creates a recursive {@link URLClassLoader} that scans the entire project hierarchy.
     *
     * @param projectRoot the root directory of the project to traverse
     * @return a {@link URLClassLoader} that can load classes from the entire project hierarchy
     * @throws Exception if URI/URL conversion or class loader initialization fails
     */
    public static ClassLoader createRecursiveProjectClassLoader(File projectRoot) throws Exception {
        final Path root = projectRoot.toPath();
        final Set<URI> uris = new LinkedHashSet<>();

        uris.add(projectRoot.toURI());

        // Add common build output directories
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isDirectory)
                    .filter(p -> p.endsWith(MAVEN_CLASSES_PATH)
                            || p.endsWith(GRADLE_CLASSES_PATH)
                            || p.endsWith(INTELLIJ_CLASSES_PATH))
                    .forEach(p -> uris.add(p.toUri()));
        }

        // Add dependency jars
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .filter(p -> isInDependencyFolder(root, p))
                    .forEach(p -> uris.add(p.toUri()));
        }

        // Add root-level *.jar files
        try (Stream<Path> s = Files.walk(root, 1)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .forEach(p -> uris.add(p.toUri()));
        }

        URL[] urls = uris.stream().map(uri -> {
            try {
                return uri.toURL();
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert URI to URL: " + uri, e);
            }
        }).toArray(URL[]::new);

        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }

    private static boolean isInDependencyFolder(Path root, Path jarPath) {
        Path relativePath = root.relativize(jarPath);
        for (Path part : relativePath) {
            String partName = part.toString();
            if (partName.equals("dependency") || partName.equals("dependencies")) {
                return true;
            }
        }
        return false;
    }
}