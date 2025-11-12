package dev.dsf.linter.plugin;

import dev.dsf.linter.logger.LogUtils;
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
import java.util.Collections;
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
public final class PluginDefinitionDiscovery {

    /**
     * Adapter interface for different plugin API versions.
     */
    public interface PluginAdapter {
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
     * Adapter for v1 ProcessPluginDefinition implementations.
     */
    public static final class V1Adapter implements PluginAdapter {
        /** The wrapped v1 ProcessPluginDefinition instance. */
        private final Object delegate;

        /** The Class object representing the v1 plugin implementation. */
        private final Class<?> delegateClass;

        /**
         * Creates adapter wrapping a v1 plugin instance.
         * @param delegate the v1 plugin implementation
         */
        public V1Adapter(Object delegate) {
            this.delegate = delegate;
            this.delegateClass = delegate.getClass();
        }

        /**
         * {@inheritDoc}
         * <p><strong>V1 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getName()} method on the v1 plugin instance using reflection.
         * The v1 API typically returns simple string names that may be used for basic identification purposes.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                          exception wrapped and the method name included in the error message
         */
        @Override
        public String getName() {
            try {
                String r = (String) delegateClass.getMethod("getName").invoke(delegate);
                return r != null ? r : "";
            } catch (Exception e) {
                throw new RuntimeException("getName", e);
            }
        }

        /**
         * {@inheritDoc}
         * <p><strong>V1 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getProcessModels()} method on the v1 plugin instance using reflection.
         * The v1 API typically returns a simple list of process identifiers without complex metadata.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                          exception wrapped and the method name included in the error message
         */
        @Override
        @SuppressWarnings("unchecked")
        public List<String> getProcessModels() {
            try {
                List<String> r = (List<String>) delegateClass.getMethod("getProcessModels").invoke(delegate);
                return r != null ? r : Collections.emptyList();
            } catch (Exception e) {
                throw new RuntimeException("getProcessModels", e);
            }
        }

        /**
         * {@inheritDoc}
         * <p><strong>V1 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getFhirResourcesByProcessId()} method on the v1 plugin instance using reflection.
         * The v1 API typically returns a simpler mapping structure compared to v2, but maintains the same basic
         * contract of mapping process IDs to FHIR resource lists.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                          exception wrapped and the method name included in the error message
         */
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, List<String>> getFhirResourcesByProcessId() {
            try {
                Map<String, List<String>> r = (Map<String, List<String>>)
                        delegateClass.getMethod("getFhirResourcesByProcessId").invoke(delegate);
                return r != null ? r : Collections.emptyMap();
            } catch (Exception e) {
                throw new RuntimeException("getFhirResourcesByProcessId", e);
            }
        }

        /**
         * {@inheritDoc}
         * <p><strong>V1 Implementation Details:</strong></p>
         * <p>Returns the Class object representing the v1 ProcessPluginDefinition implementation.
         * This can be used to identify the specific v1 plugin class and perform additional reflection operations if needed.</p>
         */
        @Override
        public Class<?> sourceClass() {
            return delegateClass;
        }
    }

    /**
     * Adapter for v2 ProcessPluginDefinition implementations.
     */
    public static final class V2Adapter implements PluginAdapter {
        /** The wrapped v2 ProcessPluginDefinition instance. */
        private final Object delegate;

        /** The Class object representing the v2 plugin implementation. */
        private final Class<?> delegateClass;

        /**
         * Creates adapter wrapping a v2 plugin instance.
         * @param delegate the v2 plugin implementation
         */
        public V2Adapter(Object delegate) {
            this.delegate = delegate;
            this.delegateClass = delegate.getClass();
        }

        /**
         * {@inheritDoc}
         * <p><strong>V2 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getName()} method on the v2 plugin instance using reflection.
         * The v2 API may provide enhanced name information including localization support or additional
         * metadata compared to v1 implementations.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                          exception wrapped and the method name included in the error message
         */
        @Override
        public String getName() {
            try {
                String r = (String) delegateClass.getMethod("getName").invoke(delegate);
                return r != null ? r : "";
            } catch (Exception e) {
                throw new RuntimeException("getName", e);
            }
        }

        /**
         * {@inheritDoc}
         * <p><strong>V2 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getProcessModels()} method on the v2 plugin instance using reflection.
         * The v2 API may provide enhanced process model information with better metadata support,
         * improved naming conventions, or additional process classification details.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                          exception wrapped and the method name included in the error message
         */
        @Override
        @SuppressWarnings("unchecked")
        public List<String> getProcessModels() {
            try {
                List<String> r = (List<String>) delegateClass.getMethod("getProcessModels").invoke(delegate);
                return r != null ? r : Collections.emptyList();
            } catch (Exception e) {
                throw new RuntimeException("getProcessModels", e);
            }
        }

        /**
         * {@inheritDoc}
         * <p><strong>V2 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getFhirResourcesByProcessId()} method on the v2 plugin instance using reflection.
         * The v2 API may provide enhanced FHIR resource mapping with additional relationship metadata,
         * better resource categorization, or improved resource profile support.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                          exception wrapped and the method name included in the error message
         */
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, List<String>> getFhirResourcesByProcessId() {
            try {
                Map<String, List<String>> r = (Map<String, List<String>>)
                        delegateClass.getMethod("getFhirResourcesByProcessId").invoke(delegate);
                return r != null ? r : Collections.emptyMap();
            } catch (Exception e) {
                throw new RuntimeException("getFhirResourcesByProcessId", e);
            }
        }

        /**
         * {@inheritDoc}
         * <p><strong>V2 Implementation Details:</strong></p>
         * <p>Returns the Class object representing the v2 ProcessPluginDefinition implementation.
         * This can be used to identify the specific v2 plugin class, access v2-specific annotations,
         * or perform advanced reflection operations that leverage v2 API enhancements.</p>
         */
        @Override
        public Class<?> sourceClass() {
            return delegateClass;
        }
    }

    /**
     * Scans the project root for plugin definitions using a recursive project ClassLoader.
     * Prefers ServiceLoader discovery (V2 first, then V1). If nothing is found,
     * falls back to a direct class scan with the same loader.
     *
     * @param projectRoot the project root directory
     * @param context discovery context to collect results and errors
     */
    static void scanProjectRoot(File projectRoot, DiscoveryContext context) {
        try {
            // Get cached recursive project class loader
            ClassLoader projectCl = getOrCreateRecursiveProjectClassLoader(projectRoot);

            // Execute discovery with temporary TCCL (automatically restored)
            ClassLoaderUtils.withTemporaryContextClassLoader(projectCl, () -> {
                logger.debug("DEBUG: Classpath (recursive) prepared via ProjectClassLoaderFactory. Trying ServiceLoader discovery...");

                // Try ServiceLoader discovery using utility method
                List<PluginAdapter> found = new ArrayList<>(ServiceLoaderUtils.discoverPluginsViaServiceLoader(projectCl));

                if (!found.isEmpty()) {
                    logger.debug("DEBUG: SUCCESS - Plugins found via ServiceLoader with recursive classpath.");
                    found.forEach(context::addSuccess);
                } else {
                    // Fallback: direct class scan using the same loader
                    logger.debug("DEBUG: ServiceLoader found nothing. Starting direct scan with recursive classpath...");
                    scanProjectClassesDirectly(projectRoot, projectCl, context);
                }
            });
        } catch (Exception e) {
            LogUtils.logAndRethrow(logger, "Plugin discovery failed", e);
        }
    }

    /**
     * Performs direct class scanning in project build directories.
     *
     * @param projectRoot the project root
     * @param projectCl   the project class loader
     * @param context discovery context to collect results and errors
     */
    private static void scanProjectClassesDirectly(File projectRoot, ClassLoader projectCl, DiscoveryContext context) {
        final Path rootPath = projectRoot.toPath();

        logger.debug("DEBUG: Starting recursive scan for build directories in " + rootPath);

        try (Stream<Path> s = Files.walk(rootPath)) {
            s.filter(Files::isDirectory)
                    // Find common build output directories in any submodule
                    .filter(p -> p.endsWith(Paths.get("target", "classes")) ||
                            p.endsWith(Paths.get("build", "classes", "java", "main")))
                    .forEach(buildDir -> {
                        logger.debug("DEBUG: Found potential build directory, scanning: " + buildDir);
                        scanDirWithClassLoader(buildDir, projectCl, context);
                    });
        } catch (IOException e) {
            System.err.println("WARNING: Failed to scan project subdirectories: " + e.getMessage());
        }

        // Fallback: If the recursive scan found nothing, scan the project root directly.
        // This handles non-standard or exploded layouts where classes might be at the root.
        if (context.getSuccessfulPlugins().isEmpty()) {
            logger.debug("DEBUG: Recursive scan found nothing, scanning project root directly...");
            scanDirWithClassLoader(rootPath, projectCl, context);
        }
    }

    /**
     * Scans directory for plugin classes using provided class loader.
     *
     * @param root the root directory
     * @param cl   the class loader to use
     * @param context discovery context to collect results and errors
     */
    private static void scanDirWithClassLoader(Path root, ClassLoader cl, DiscoveryContext context) {
        try (Stream<Path> s = Files.walk(root)) {
            isProcessPluginDefinitionClassFile(root, cl, context, s);
        } catch (Exception ignored) {
        }
    }

    /**
     * Result container for plugin discovery operations.
     */
    public static class DiscoveryContext {
        private final List<PluginAdapter> successfulPlugins = new ArrayList<>();
        private final List<PluginDiscoveryError> failedPlugins = new ArrayList<>();
        
        public void addSuccess(PluginAdapter adapter) {
            successfulPlugins.add(adapter);
        }
        
        public void addFailure(PluginDiscoveryError error) {
            failedPlugins.add(error);
        }
        
        public List<PluginAdapter> getSuccessfulPlugins() {
            return successfulPlugins;
        }
        
        public List<PluginDiscoveryError> getFailedPlugins() {
            return failedPlugins;
        }
    }
    
    /**
     * Filters and lints ProcessPluginDefinition class files from stream.
     * Performs interface implementation and method signature linting.
     *
     * @param root the root path for class loading
     * @param cl   class loader for linting
     * @param context  discovery context to collect results and errors
     * @param s    stream of file paths to process
     */
    private static void isProcessPluginDefinitionClassFile(Path root, ClassLoader cl, DiscoveryContext context, Stream<Path> s) {
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
                            // FAILURE case 1: Does not implement the interface at all
                            logger.debug("DEBUG: FAILED - Candidate class does not implement the ProcessPluginDefinition interface.");
                            logger.debug("  - Candidate class: " + c.getName());
                            logger.debug("  - From root: " + root.toAbsolutePath());
                            return; // Stop processing this class
                        }

                        // At this point, the class IMPLEMENTS the interface. Now check if methods are also present.
                        boolean hasRequiredMethods = PluginLintingUtils.hasPluginSignature(c);
                        if (hasRequiredMethods) {
                            // --- SUCCESS CASE ---
                            // Only succeeds if it implements the interface AND has the methods.
                            logger.debug("DEBUG: SUCCESS - Found valid plugin definition.");
                            logger.debug("  - Found class: " + c.getName());
                            logger.debug("  - From root: " + root.toAbsolutePath());
                            logger.debug("  - Linter: method + Implements Interface AND has required methods.");

                            Object inst = c.getDeclaredConstructor().newInstance();

                            // Determine version and create appropriate adapter
                            boolean isV2 = PluginLintingUtils.isV2Plugin(c, cl);
                            if (isV2) {
                                logger.debug("  - Version: V2 (creating V2Adapter)");
                                context.addSuccess(new V2Adapter(inst));
                            } else {
                                boolean isV1 = PluginLintingUtils.isV1Plugin(c, cl);
                                if (isV1) {
                                    logger.debug("  - Version: V1 (creating V1Adapter)");
                                    context.addSuccess(new V1Adapter(inst));
                                } else {
                                    // ERROR: Plugin implements neither V1 nor V2!
                                    // Collect error instead of throwing exception (partial success)
                                    logger.error("✗ Plugin discovery failed: " + c.getName());
                                    logger.error("  Reason: Does not implement valid DSF API interface (neither v1 nor v2)");
                                    logger.error("  Location: " + root.toAbsolutePath());
                                    
                                    PluginDiscoveryError error = PluginDiscoveryError.invalidApiVersion(
                                            c.getName(),
                                            root.toAbsolutePath().toString()
                                    );
                                    context.addFailure(error);
                                    // Continue with next plugin instead of throwing exception
                                }
                            }
                        } else {
                            // FAILURE case 2: Implements interface, but is missing methods.
                            logger.debug("DEBUG: FAILED - Class implements ProcessPluginDefinition but is missing required methods.");
                            logger.debug("  - Candidate class: " + c.getName());
                            logger.debug("  - From root: " + root.toAbsolutePath());
                        }
                    } catch (Throwable ignored) {
                    }
                });
    }

    /**
     * Scans JAR files on classpath for plugin definitions.
     *
     * @param parentCl parent class loader
     * @return list of plugins found in JARs
     */
    static List<PluginAdapter> scanJars(ClassLoader parentCl) {
        List<PluginAdapter> found = new ArrayList<>();
        String cp = System.getProperty("java.class.path", "");
        String sep = File.pathSeparator;

        for (String e : cp.split(sep)) { // e is the JAR file path, which is our "root"
            if (!e.endsWith(".jar")) continue;

            try (JarFile jar = new JarFile(e)) {
                URLClassLoader jarCl = new URLClassLoader(
                        new java.net.URL[]{new File(e).toURI().toURL()},
                        parentCl
                );

                Enumeration<JarEntry> it = jar.entries();
                while (it.hasMoreElements()) {
                    JarEntry je = it.nextElement();
                    if (je.isDirectory()) continue;

                    String name = je.getName();
                    if (!name.endsWith("ProcessPluginDefinition.class") || name.contains("$")) {
                        continue;
                    }

                    String fqcn = name.replace('/', '.').replace(".class", "");

                    try {
                        Class<?> c = Class.forName(fqcn, false, jarCl);

                        boolean implementsInterface = PluginLintingUtils.implementsProcessPluginDefinition(c, jarCl);
                        if (!implementsInterface) {
                            // FAILURE case 1
                            logger.debug("DEBUG: FAILED - Candidate in JAR does not implement ProcessPluginDefinition interface.");
                            logger.debug("  - Candidate class: " + c.getName());
                            logger.debug("  - From root: " + e);
                            continue; // Skip to next class in JAR
                        }

                        boolean hasRequiredMethods = PluginLintingUtils.hasPluginSignature(c);
                        if (hasRequiredMethods) {
                            // --- SUCCESS CASE ---
                            logger.debug("DEBUG: SUCCESS - Found valid plugin definition in JAR.");
                            logger.debug("  - Found class: " + c.getName());
                            logger.debug("  - From root: " + e);

                            Object inst = c.getDeclaredConstructor().newInstance();

                            // Determine version and create appropriate adapter
                            boolean isV2 = PluginLintingUtils.isV2Plugin(c, jarCl);
                            if (isV2) {
                                logger.debug("  - Version: V2 (creating V2Adapter)");
                                found.add(new V2Adapter(inst));
                            } else {
                                boolean isV1 = PluginLintingUtils.isV1Plugin(c, jarCl);
                                if (isV1) {
                                    logger.debug("  - Version: V1 (creating V1Adapter)");
                                    found.add(new V1Adapter(inst));
                                } else {
                                    // ERROR: Plugin implements neither V1 nor V2!
                                    // Log error but continue with next plugin (partial success)
                                    logger.error("✗ Plugin discovery failed: " + c.getName());
                                    logger.error("  Reason: Does not implement valid DSF API interface (neither v1 nor v2)");
                                    logger.error("  Location (JAR): " + e);
                                    // Note: In JAR scan, we don't have a DiscoveryContext, so we just skip
                                    // This is acceptable since scanJars is a fallback method
                                }
                            }
                        } else {
                            // FAILURE case 2
                            logger.debug("DEBUG: FAILED - Class in JAR implements interface but is missing required methods.");
                            logger.debug("  - Candidate class: " + c.getName());
                            logger.debug("  - From root: " + e);
                        }
                    } catch (Throwable ignored) {
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
     *
     * @param parentCl parent class loader
     * @return list of discovered plugins
     */
    static List<PluginAdapter> scanDirectories(ClassLoader parentCl) {
        List<PluginAdapter> found = new ArrayList<>();
        String[] scanPaths = {"target/classes", "build/classes/java/main", "out/production/classes"};

        for (String scanPath : scanPaths) {
            Path path = Paths.get(scanPath);
            if (Files.exists(path) && Files.isDirectory(path)) {
                found.addAll(scanDir(path, parentCl));
            }
        }

        return found;
    }

    /**
     * Scans single directory for plugin definitions.
     *
     * @param root     the directory to scan
     * @param parentCl parent class loader
     * @return list of discovered plugins
     */
    private static List<PluginAdapter> scanDir(Path root, ClassLoader parentCl) {
        DiscoveryContext context = new DiscoveryContext();
        try (Stream<Path> s = Files.walk(root)) {
            URLClassLoader dirCl = new URLClassLoader(new URL[]{root.toUri().toURL()}, parentCl);
            isProcessPluginDefinitionClassFile(root, dirCl, context, s);
        } catch (Exception ignored) {
        }
        // For backward compatibility, only return successful plugins
        // (scanDir is a fallback method and doesn't currently propagate errors)
        return context.getSuccessfulPlugins();
    }

    /**
     * Converts file path to fully qualified class name.
     *
     * @param root  the root path
     * @param clazz the class file path
     * @return fully qualified class name
     */
    private static String toFqdn(Path root, Path clazz) {
        String rel = root.relativize(clazz).toString();
        return rel.substring(0, rel.length() - ".class".length()).replace(File.separatorChar, '.');
    }
}
