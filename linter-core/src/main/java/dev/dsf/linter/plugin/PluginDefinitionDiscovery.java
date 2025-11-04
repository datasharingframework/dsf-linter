package dev.dsf.linter.plugin;

import dev.dsf.linter.util.LogUtils;
import dev.dsf.linter.util.loader.ClassLoaderUtils;
import dev.dsf.linter.util.linting.PluginLintingUtils;
import dev.dsf.linter.util.loader.ServiceLoaderUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static dev.dsf.linter.classloading.ClassInspector.logger;
import static dev.dsf.linter.classloading.ProjectClassLoaderFactory.getOrCreateRecursiveProjectClassLoader;

/**
 * Discovers and loads ProcessPluginDefinition implementations from the classpath.
 * Supports both v1 and v2 plugin APIs using ServiceLoader and manual scanning fallback.
 */
public final class PluginDefinitionDiscovery
{
    /**
     * Adapter interface for different plugin API versions.
     */
    public interface PluginAdapter
    {
        /** Returns the plugin name. */
        String getName();

        /** Returns list of process model identifiers. */
        List<String> getProcessModels();

        /** Returns FHIR resources mapped by process ID. */
        Map<String, List<String>> getFhirResourcesByProcessId();

        /** Returns the underlying plugin class. */
        Class<?> sourceClass();
    }


    /**
     * Scans the project root for plugin definitions using a recursive project ClassLoader.
     * Prefers ServiceLoader discovery (V2 first, then V1). If nothing is found, falls back
     * to a direct class scan with the same loader.
     *
     * @param projectRoot the project root directory
     * @return list of discovered PluginAdapter instances
     */
    static List<PluginAdapter> scanProjectRoot(File projectRoot) {
        try {
            // Get cached recursive project class loader
            ClassLoader projectCl = getOrCreateRecursiveProjectClassLoader(projectRoot);

            // Execute discovery with temporary TCCL (automatically restored)
            return ClassLoaderUtils.withTemporaryContextClassLoader(projectCl, () -> {

                logger.debug("[DEBUG] Classpath (recursive) prepared via ProjectClassLoaderFactory. Trying ServiceLoader discovery...");

                // Try ServiceLoader discovery using utility method
                List<PluginAdapter> found = new ArrayList<>(ServiceLoaderUtils.discoverPluginsViaServiceLoader(projectCl));

                if (!found.isEmpty()) {
                    logger.debug("[DEBUG] SUCCESS: Plugin(s) found via ServiceLoader with recursive classpath.");
                } else {
                    // Fallback: direct class scan using the same loader
                    logger.debug("[DEBUG] ServiceLoader found nothing. Starting direct scan with recursive classpath...");
                    found.addAll(scanProjectClassesDirectly(projectRoot, projectCl));
                }

                return found;
            });

        } catch (Exception e) {
            LogUtils.logAndRethrow(logger, "Plugin discovery failed", e);
            return new ArrayList<>(); // Never reached, but needed for compilation
        }
    }


    /**
     * Performs direct class scanning in project build directories.
     * @param projectRoot the project root
     * @param projectCl the project class loader
     * @return list of found plugins
     */
    private static List<PluginAdapter> scanProjectClassesDirectly(File projectRoot, ClassLoader projectCl) {
        final List<PluginAdapter> found = new ArrayList<>();
        final Path rootPath = projectRoot.toPath();

        logger.debug("[DEBUG] Starting recursive scan for build directories in: " + rootPath);

        try (Stream<Path> s = Files.walk(rootPath)) {
            s.filter(Files::isDirectory)
                    // Find common build output directories in any submodule
                    .filter(p -> p.endsWith(Paths.get("target", "classes")) || p.endsWith(Paths.get("build", "classes", "java", "main")))
                    .forEach(buildDir -> {
                        logger.debug("[DEBUG] Found potential build directory, scanning: " + buildDir);
                        found.addAll(scanDirWithClassLoader(buildDir, projectCl));
                    });
        } catch (IOException e) {
            System.err.println("WARNING: Failed to scan project subdirectories: " + e.getMessage());
        }

        // Fallback: If the recursive scan found nothing, scan the project root directly.
        // This handles non-standard or "exploded" layouts where classes might be at the root.
        if (found.isEmpty()) {
            logger.debug("[DEBUG] Recursive scan found nothing, scanning project root directly...");
            found.addAll(scanDirWithClassLoader(rootPath, projectCl));
        }

        return found;
    }


    /**
     * Scans directory for plugin classes using provided class loader.
     * @param root the root directory
     * @param cl the class loader to use
     * @return list of discovered plugins
     */
    private static List<PluginAdapter> scanDirWithClassLoader(Path root, ClassLoader cl) {
        List<PluginAdapter> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            isProcessPluginDefinitionClassFile(root, cl, out, s);
        } catch (Exception ignored) {}
        return out;
    }

    /**
     * Filters and lints ProcessPluginDefinition class files from stream.
     * Performs interface implementation and method signature linting.
     * @param root the root path for class loading
     * @param cl class loader for linting
     * @param out output list to add valid plugins
     * @param s stream of file paths to process
     */
    private static void isProcessPluginDefinitionClassFile(Path root, ClassLoader cl, List<PluginAdapter> out, Stream<Path> s) {
        s.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith("ProcessPluginDefinition.class"))
                .filter(p -> !p.getFileName().toString().contains("$"))
                .forEach(p -> {
                    String fqdn = toFqdn(root, p);
                    try {
                        Class<?> c = Class.forName(fqdn, false, cl);

                        // --- Strict, step-by-step linting logic ---
                        boolean implementsInterface = PluginLintingUtils.implementsProcessPluginDefinition(c, cl);
                        if (!implementsInterface) {
                            // FAILURE case 1: Does not implement the interface at all.
                            logger.debug("[DEBUG] FAILED: Candidate class does not implement the 'ProcessPluginDefinition' interface.");
                            logger.debug("  -> Candidate class: " + c.getName());
                            logger.debug("     -> From root: " + root.toAbsolutePath());
                            return; // Stop processing this class
                        }

                        // At this point, the class IMPLEMENTS the interface. Now check if methods are also present.
                        boolean hasRequiredMethods = PluginLintingUtils.hasPluginSignature(c);
                        if (hasRequiredMethods) {
                            // --- SUCCESS CASE ---
                            // Only succeeds if it implements the interface AND has the methods.
                            logger.debug("[DEBUG] SUCCESS: Found valid plugin definition.");
                            logger.debug("  -> Found class: " + c.getName());
                            logger.debug("     -> From root: " + root.toAbsolutePath());
                            logger.debug("     -> Linter method: Implements Interface AND has required methods.");

                            Object inst = c.getDeclaredConstructor().newInstance();
                            GenericPluginAdapter.ApiVersion version = PluginLintingUtils.isV2Plugin(c, cl)
                                    ? GenericPluginAdapter.ApiVersion.V2
                                    : GenericPluginAdapter.ApiVersion.V1;
                            out.add(new GenericPluginAdapter(inst, version));
                        } else {
                            // FAILURE case 2: Implements interface, but is missing methods.
                            logger.debug("[DEBUG] FAILED: Class implements 'ProcessPluginDefinition' but is missing required methods.");
                            logger.debug("  -> Candidate class: " + c.getName());
                            logger.debug("     -> From root: " + root.toAbsolutePath());
                        }
                    } catch (Throwable ignored) {}
                });
    }


    /**
     * Scans JAR files on classpath for plugin definitions.
     * @param parentCl parent class loader
     * @return list of plugins found in JARs
     */
    static List<PluginAdapter> scanJars(ClassLoader parentCl) {
        List<PluginAdapter> found = new ArrayList<>();
        String cp = System.getProperty("java.class.path", "");
        String sep = File.pathSeparator;
        for (String e : cp.split(sep)) { // 'e' is the JAR file path, which is our root
            if (!e.endsWith(".jar")) continue;
            try (JarFile jar = new JarFile(e)) {
                try (URLClassLoader jarCl = new URLClassLoader(new java.net.URL[]{ new File(e).toURI().toURL() }, parentCl)) {
                    Enumeration<JarEntry> it = jar.entries();
                    while (it.hasMoreElements()) {
                        JarEntry je = it.nextElement();
                        if (je.isDirectory()) continue;
                        String name = je.getName();
                        if (!name.endsWith("ProcessPluginDefinition.class") || name.contains("$")) continue;
                        String fqcn = name.replace('/', '.').replace(".class", "");
                        try {
                            Class<?> c = Class.forName(fqcn, false, jarCl);

                            boolean implementsInterface = PluginLintingUtils.implementsProcessPluginDefinition(c, jarCl);
                            if (!implementsInterface) {
                                // FAILURE case 1
                                logger.debug("[DEBUG] FAILED: Candidate in JAR does not implement 'ProcessPluginDefinition' interface.");
                                logger.debug("  -> Candidate class: " + c.getName());
                                logger.debug("     -> From root: " + e);
                                continue; // Skip to next class in JAR
                            }

                            boolean hasRequiredMethods = PluginLintingUtils.hasPluginSignature(c);
                            if (hasRequiredMethods) {
                                // --- SUCCESS CASE ---
                                logger.debug("[DEBUG] SUCCESS: Found valid plugin definition in JAR.");
                                logger.debug("  -> Found class: " + c.getName());
                                logger.debug("     -> From root: " + e);

                                Object inst = c.getDeclaredConstructor().newInstance();
                                GenericPluginAdapter.ApiVersion version = PluginLintingUtils.isV2Plugin(c, jarCl)
                                        ? GenericPluginAdapter.ApiVersion.V2
                                        : GenericPluginAdapter.ApiVersion.V1;
                                found.add(new GenericPluginAdapter(inst, version));
                            } else {
                                // FAILURE case 2
                                logger.debug("[DEBUG] FAILED: Class in JAR implements interface but is missing required methods.");
                                logger.debug("  -> Candidate class: " + c.getName());
                                logger.debug("     -> From root: " + e);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return found;
    }

    /**
     * Scans standard build output directories for plugins.
     * @param parentCl parent class loader
     * @return list of discovered plugins
     */
    static List<PluginAdapter> scanDirectories(ClassLoader parentCl) {
        List<PluginAdapter> found = new ArrayList<>();
        String[] scanPaths = { "target/classes", "build/classes/java/main", "out/production/classes" };
        for (String scanPath : scanPaths) {
            Path path = Paths.get(scanPath);
            if (Files.exists(path) && Files.isDirectory(path)) found.addAll(scanDir(path, parentCl));
        }
        return found;
    }

    /**
     * Scans single directory for plugin definitions.
     * @param root the directory to scan
     * @param parentCl parent class loader
     * @return list of discovered plugins
     */
    private static List<PluginAdapter> scanDir(Path root, ClassLoader parentCl) {
        List<PluginAdapter> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            try (URLClassLoader dirCl = new URLClassLoader(new URL[]{ root.toUri().toURL() }, parentCl)) {
                isProcessPluginDefinitionClassFile(root, dirCl, (List<PluginAdapter>) out, s);
            }
        } catch (Exception ignored) {}
        return out;
    }

    /**
     * Converts file path to fully qualified class name.
     * @param root the root path
     * @param clazz the class file path
     * @return fully qualified class name
     */
    private static String toFqdn(Path root, Path clazz) {
        String rel = root.relativize(clazz).toString();
        return rel.substring(0, rel.length() - ".class".length()).replace(File.separatorChar, '.');
    }
}