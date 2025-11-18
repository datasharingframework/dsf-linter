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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Factory for creating ClassLoaders for extracted JAR projects.
 * <p>
 * This factory creates ClassLoaders that load classes and resources from extracted
 * JAR directories. Since JARs are extracted as flat directory structures, there's
 * no need to scan for Maven/Gradle build directories.
 * </p>
 */
public class ProjectClassLoaderFactory {

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
     * <p>This method provides thread-safe, cached access to a standard class loader.
     * The instance is stored in {@link #CL_CACHE} to avoid the cost of recreating it on subsequent calls
     * for the same project. The cache key is the canonical path of the project root.</p>
     *
     * @param projectRoot the root directory of the extracted JAR project
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
     * Creates a {@link URLClassLoader} configured to load classes and resources from an extracted JAR directory.
     * <p>
     * For extracted JARs, this method:
     * <ul>
     *   <li>Adds the project root directory (contains all extracted classes)</li>
     *   <li>Adds any JAR files in the root directory (for nested dependencies)</li>
     * </ul>
     * </p>
     *
     * @param projectRoot the root directory of the extracted JAR project
     * @return a {@link URLClassLoader} that can load project classes and dependencies
     * @throws Exception if URL conversion or class loader initialization fails
     */
    public static ClassLoader createProjectClassLoader(File projectRoot) throws Exception {
        List<URL> urls = new ArrayList<>();
        Path rootPath = projectRoot.toPath();

        urls.add(rootPath.toUri().toURL());

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rootPath, "*.jar")) {
            for (Path jar : ds) {
                urls.add(jar.toUri().toURL());
            }
        }

        return new URLClassLoader(urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates a recursive {@link URLClassLoader} that scans the entire project hierarchy.
     * <p>
     * This scans for:
     * <ul>
     *   <li>The project root directory</li>
     *   <li>All JAR files in the hierarchy</li>
     * </ul>
     * </p>
     *
     * @param projectRoot the root directory of the project to traverse
     * @return a {@link URLClassLoader} that can load classes from the entire project hierarchy
     * @throws Exception if URI/URL conversion or class loader initialization fails
     */
    public static ClassLoader createRecursiveProjectClassLoader(File projectRoot) throws Exception {
        final Path root = projectRoot.toPath();
        final Set<URI> uris = new LinkedHashSet<>();

        uris.add(projectRoot.toURI());

        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jar"))
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
}