package dev.dsf.utils.validator.plugin;

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
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static dev.dsf.utils.validator.classloading.ProjectClassLoaderFactory.getOrCreateRecursiveProjectClassLoader;

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
     * Adapter for v1 ProcessPluginDefinition implementations.
     */
    public static final class V1Adapter implements PluginAdapter
    {
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
         *
         * <p><strong>V1 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getName()} method on the v1 plugin instance using reflection.
         * The v1 API typically returns simple string names that may be used for basic identification purposes.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                         exception wrapped and the method name included in the error message
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
         *
         * <p><strong>V1 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getProcessModels()} method on the v1 plugin instance using reflection.
         * The v1 API typically returns a simple list of process identifiers without complex metadata.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                         exception wrapped and the method name included in the error message
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
         *
         * <p><strong>V1 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getFhirResourcesByProcessId()} method on the v1 plugin instance
         * using reflection. The v1 API typically returns a simpler mapping structure compared to v2,
         * but maintains the same basic contract of mapping process IDs to FHIR resource lists.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                         exception wrapped and the method name included in the error message
         */
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, List<String>> getFhirResourcesByProcessId() {
            try {
                Map<String, List<String>> r = (Map<String, List<String>>) delegateClass.getMethod("getFhirResourcesByProcessId").invoke(delegate);
                return r != null ? r : Collections.emptyMap();
            } catch (Exception e) {
                throw new RuntimeException("getFhirResourcesByProcessId", e);
            }
        }

        /**
         * {@inheritDoc}
         *
         * <p><strong>V1 Implementation Details:</strong></p>
         * <p>Returns the Class object representing the v1 ProcessPluginDefinition implementation.
         * This can be used to identify the specific v1 plugin class and perform additional
         * reflection operations if needed.</p>
         */
        @Override
        public Class<?> sourceClass() {
            return delegateClass;
        }
    }


    /**
     * Adapter for v2 ProcessPluginDefinition implementations.
     */
    public static final class V2Adapter implements PluginAdapter
    {
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
         *
         * <p><strong>V2 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getName()} method on the v2 plugin instance using reflection.
         * The v2 API may provide enhanced name information including localization support or
         * additional metadata compared to v1 implementations.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                         exception wrapped and the method name included in the error message
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
         *
         * <p><strong>V2 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getProcessModels()} method on the v2 plugin instance using reflection.
         * The v2 API may provide enhanced process model information with better metadata support,
         * improved naming conventions, or additional process classification details.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                         exception wrapped and the method name included in the error message
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
         *
         * <p><strong>V2 Implementation Details:</strong></p>
         * <p>This method invokes the {@code getFhirResourcesByProcessId()} method on the v2 plugin instance
         * using reflection. The v2 API may provide enhanced FHIR resource mapping with additional
         * relationship metadata, better resource categorization, or improved resource profile support.</p>
         *
         * @throws RuntimeException if the reflection-based method call fails, with the original
         *                         exception wrapped and the method name included in the error message
         */
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, List<String>> getFhirResourcesByProcessId() {
            try {
                Map<String, List<String>> r = (Map<String, List<String>>) delegateClass.getMethod("getFhirResourcesByProcessId").invoke(delegate);
                return r != null ? r : Collections.emptyMap();
            } catch (Exception e) {
                throw new RuntimeException("getFhirResourcesByProcessId", e);
            }
        }

        /**
         * {@inheritDoc}
         *
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
     * Scans project root recursively for plugin definitions.
     * @param projectRoot the project root directory
     * @return list of discovered plugins
     */
    static List<PluginAdapter> scanProjectRoot(File projectRoot) {
        List<PluginAdapter> found = new ArrayList<>();

        try {
            // 1) Get cached recursive project class loader from the utils
            ClassLoader projectCl = getOrCreateRecursiveProjectClassLoader(projectRoot);

            // 2) Temporarily set TCCL so ServiceLoader.load(service) uses it
            ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(projectCl);

            try {
                System.out.println("[DEBUG] Classpath (recursive) built via BpmnValidationUtils. Trying ServiceLoader discovery...");

                // 3) Try v2 first (prefer v2 if both exist)
                try {
                    Class<?> v2Class = Class.forName("dev.dsf.bpe.v2.ProcessPluginDefinition", false, projectCl);
                    ServiceLoader.load(v2Class, projectCl).forEach(instance -> found.add(new V2Adapter(instance)));
                } catch (ClassNotFoundException ignored) {}

                // 4) Then try v1
                try {
                    Class<?> v1Class = Class.forName("dev.dsf.bpe.v1.ProcessPluginDefinition", false, projectCl);
                    ServiceLoader.load(v1Class, projectCl).forEach(instance -> found.add(new V1Adapter(instance)));
                } catch (ClassNotFoundException ignored) {}

                if (!found.isEmpty()) {
                    System.out.println("[DEBUG] SUCCESS: Plugin found via ServiceLoader with recursive classpath.");
                } else {
                    // 5) Fallback: direct class scan using the same loader
                    System.out.println("[DEBUG] ServiceLoader found nothing. Starting direct scan with recursive classpath...");
                    found.addAll(scanProjectClassesDirectly(projectRoot, projectCl));
                }
            } finally {
                // 6) Always restore TCCL
                Thread.currentThread().setContextClassLoader(oldCl);
            }

        } catch (Exception e) {
            System.err.println("WARNING: Project root scanning failed critically: " + e.getMessage());
            e.printStackTrace();
        }

        return found;
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

        System.out.println("[DEBUG] Starting recursive scan for build directories in: " + rootPath);

        try (Stream<Path> s = Files.walk(rootPath)) {
            s.filter(Files::isDirectory)
                    // Find common build output directories in any submodule
                    .filter(p -> p.endsWith(Paths.get("target", "classes")) || p.endsWith(Paths.get("build", "classes", "java", "main")))
                    .forEach(buildDir -> {
                        System.out.println("[DEBUG] Found potential build directory, scanning: " + buildDir);
                        found.addAll(scanDirWithClassLoader(buildDir, projectCl));
                    });
        } catch (IOException e) {
            System.err.println("WARNING: Failed to scan project subdirectories: " + e.getMessage());
        }

        // Fallback: If the recursive scan found nothing, scan the project root directly.
        // This handles non-standard or "exploded" layouts where classes might be at the root.
        if (found.isEmpty()) {
            System.out.println("[DEBUG] Recursive scan found nothing, scanning project root directly...");
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
     * Filters and validates ProcessPluginDefinition class files from stream.
     * Performs interface implementation and method signature validation.
     * @param root the root path for class loading
     * @param cl class loader for validation
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

                        // --- Strict, step-by-step validation logic ---
                        boolean implementsInterface = implementsProcessPluginDefinition(c, cl);
                        if (!implementsInterface) {
                            // FAILURE case 1: Does not implement the interface at all.
                            System.out.println("[DEBUG] FAILED: Candidate class does not implement the 'ProcessPluginDefinition' interface.");
                            System.out.println("  -> Candidate class: " + c.getName());
                            System.out.println("     -> From root: " + root.toAbsolutePath());
                            return; // Stop processing this class
                        }

                        // At this point, the class IMPLEMENTS the interface. Now check if methods are also present.
                        boolean hasRequiredMethods = hasPluginSignature(c);
                        if (hasRequiredMethods) {
                            // --- SUCCESS CASE ---
                            // Only succeeds if it implements the interface AND has the methods.
                            System.out.println("[DEBUG] SUCCESS: Found valid plugin definition.");
                            System.out.println("  -> Found class: " + c.getName());
                            System.out.println("     -> From root: " + root.toAbsolutePath());
                            System.out.println("     -> Validation method: Implements Interface AND has required methods.");

                            Object inst = c.getDeclaredConstructor().newInstance();
                            if (isAssignableTo(c, cl))
                                out.add(new V2Adapter(inst));
                            else
                                out.add(new V1Adapter(inst));
                        } else {
                            // FAILURE case 2: Implements interface, but is missing methods.
                            System.out.println("[DEBUG] FAILED: Class implements 'ProcessPluginDefinition' but is missing required methods.");
                            System.out.println("  -> Candidate class: " + c.getName());
                            System.out.println("     -> From root: " + root.toAbsolutePath());
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

                            boolean implementsInterface = implementsProcessPluginDefinition(c, jarCl);
                            if (!implementsInterface) {
                                // FAILURE case 1
                                System.out.println("[DEBUG] FAILED: Candidate in JAR does not implement 'ProcessPluginDefinition' interface.");
                                System.out.println("  -> Candidate class: " + c.getName());
                                System.out.println("     -> From root: " + e);
                                continue; // Skip to next class in JAR
                            }

                            boolean hasRequiredMethods = hasPluginSignature(c);
                            if (hasRequiredMethods) {
                                // --- SUCCESS CASE ---
                                System.out.println("[DEBUG] SUCCESS: Found valid plugin definition in JAR.");
                                System.out.println("  -> Found class: " + c.getName());
                                System.out.println("     -> From root: " + e);

                                Object inst = c.getDeclaredConstructor().newInstance();
                                if (isAssignableTo(c, jarCl))
                                    found.add(new V2Adapter(inst));
                                else
                                    found.add(new V1Adapter(inst));
                            } else {
                                // FAILURE case 2
                                System.out.println("[DEBUG] FAILED: Class in JAR implements interface but is missing required methods.");
                                System.out.println("  -> Candidate class: " + c.getName());
                                System.out.println("     -> From root: " + e);
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
     * Checks if class implements ProcessPluginDefinition interface.
     * @param c the class to check
     * @param cl class loader for interface lookup
     * @return true if class implements the interface
     */
    private static boolean implementsProcessPluginDefinition(Class<?> c, ClassLoader cl) {
        try {
            if (Class.forName("dev.dsf.bpe.v2.ProcessPluginDefinition", false, cl).isAssignableFrom(c)) return true;
        } catch (ClassNotFoundException ignored) {

        }
        try {
            if (Class.forName("dev.dsf.bpe.v1.ProcessPluginDefinition", false, cl).isAssignableFrom(c)) return true;
        } catch (ClassNotFoundException ignored) {

        }
        return false;
    }

    /**
     * Verifies class has required plugin method signatures.
     * @param c the class to check
     * @return true if all required methods are present
     */
    private static boolean hasPluginSignature(Class<?> c) {
        try {
            c.getMethod("getName");
            c.getMethod("getProcessModels");
            c.getMethod("getFhirResourcesByProcessId");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
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

    /**
     * Checks if class is assignable to v2 plugin interface.
     * @param c the class to check
     * @param cl class loader for interface lookup
     * @return true if assignable to v2 interface
     */
    private static boolean isAssignableTo(Class<?> c, ClassLoader cl) {
        try {
            return Class.forName("dev.dsf.bpe.v2.ProcessPluginDefinition", false, cl).isAssignableFrom(c);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
