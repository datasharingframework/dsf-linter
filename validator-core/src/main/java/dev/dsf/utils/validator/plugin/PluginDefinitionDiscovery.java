package dev.dsf.utils.validator.plugin;

import dev.dsf.utils.validator.util.BpmnValidationUtils;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for discovering ProcessPluginDefinition implementations at runtime.
 *
 * <p>This class provides mechanisms to locate and load plugin definitions that implement
 * either the v1 or v2 ProcessPluginDefinition interface. It supports multiple discovery
 * strategies including Java ServiceLoader, JAR scanning, directory scanning, and enhanced
 * project-based class loading.</p>
 *
 * <p>The discovery process follows a priority order:
 * <ol>
 *   <li>Java ServiceLoader with current context class loader</li>
 *   <li>Manual JAR scanning from classpath</li>
 *   <li>Directory scanning of common build output locations</li>
 *   <li>Enhanced project root scanning (if project root is provided)</li>
 * </ol>
 * </p>
 *
 * <p>Discovered plugins are wrapped in adapter instances that provide a uniform interface
 * regardless of the underlying plugin version (v1 or v2).</p>
 *
 * @since 1.0
 */
public final class PluginDefinitionDiscovery
{
    /**
     * Adapter interface that provides a uniform API for accessing plugin definitions
     * regardless of their underlying version (v1 or v2).
     *
     * <p>This interface abstracts the differences between different plugin API versions
     * and provides consistent access to plugin metadata and resources.</p>
     */
    public interface PluginAdapter
    {
        /**
         * Returns the name of the plugin.
         *
         * @return the plugin name, never null (empty string if not available)
         */
        String getName();

        /**
         * Returns the list of process models provided by this plugin.
         *
         * @return list of process model identifiers, never null (empty list if none available)
         */
        List<String> getProcessModels();

        /**
         * Returns a mapping of process IDs to their associated FHIR resources.
         *
         * @return map where keys are process IDs and values are lists of FHIR resource identifiers,
         *         never null (empty map if no resources available)
         */
        Map<String, List<String>> getFhirResourcesByProcessId();

        /**
         * Returns the underlying source class of the plugin definition.
         *
         * @return the class object representing the plugin implementation
         */
        Class<?> sourceClass();
    }

    /**
     * Adapter implementation for v1 ProcessPluginDefinition implementations.
     *
     * <p>This adapter uses reflection to invoke methods on v1 plugin instances,
     * providing a consistent interface while maintaining compatibility with
     * the v1 plugin API.</p>
     */
    public static final class V1Adapter implements PluginAdapter
    {
        private final Object delegate;
        private final Class<?> delegateClass;

        /**
         * Creates a new V1Adapter wrapping the specified plugin instance.
         *
         * @param delegate the v1 plugin instance to wrap
         * @throws IllegalArgumentException if delegate is null
         */
        public V1Adapter(Object delegate) { this.delegate = delegate; this.delegateClass = delegate.getClass(); }

        @Override public String getName() { try { String r=(String)delegateClass.getMethod("getName").invoke(delegate); return r!=null?r:""; } catch (Exception e) { throw new RuntimeException("getName", e); } }
        @Override @SuppressWarnings("unchecked") public List<String> getProcessModels() { try { List<String> r=(List<String>)delegateClass.getMethod("getProcessModels").invoke(delegate); return r!=null?r:Collections.emptyList(); } catch (Exception e) { throw new RuntimeException("getProcessModels", e); } }
        @Override @SuppressWarnings("unchecked") public Map<String, List<String>> getFhirResourcesByProcessId() { try { Map<String,List<String>> r=(Map<String,List<String>>)delegateClass.getMethod("getFhirResourcesByProcessId").invoke(delegate); return r!=null?r:Collections.emptyMap(); } catch (Exception e) { throw new RuntimeException("getFhirResourcesByProcessId", e); } }
        @Override public Class<?> sourceClass() { return delegateClass; }
    }

    /**
     * Adapter implementation for v2 ProcessPluginDefinition implementations.
     *
     * <p>This adapter uses reflection to invoke methods on v2 plugin instances,
     * providing a consistent interface while maintaining compatibility with
     * the v2 plugin API.</p>
     */
    public static final class V2Adapter implements PluginAdapter
    {
        private final Object delegate;
        private final Class<?> delegateClass;

        /**
         * Creates a new V2Adapter wrapping the specified plugin instance.
         *
         * @param delegate the v2 plugin instance to wrap
         * @throws IllegalArgumentException if delegate is null
         */
        public V2Adapter(Object delegate) { this.delegate = delegate; this.delegateClass = delegate.getClass(); }

        @Override public String getName() { try { String r=(String)delegateClass.getMethod("getName").invoke(delegate); return r!=null?r:""; } catch (Exception e) { throw new RuntimeException("getName", e); } }
        @Override @SuppressWarnings("unchecked") public List<String> getProcessModels() { try { List<String> r=(List<String>)delegateClass.getMethod("getProcessModels").invoke(delegate); return r!=null?r:Collections.emptyList(); } catch (Exception e) { throw new RuntimeException("getProcessModels", e); } }
        @Override @SuppressWarnings("unchecked") public Map<String, List<String>> getFhirResourcesByProcessId() { try { Map<String,List<String>> r=(Map<String,List<String>>)delegateClass.getMethod("getFhirResourcesByProcessId").invoke(delegate); return r!=null?r:Collections.emptyMap(); } catch (Exception e) { throw new RuntimeException("getFhirResourcesByProcessId", e); } }
        @Override public Class<?> sourceClass() { return delegateClass; }
    }

    /**
     * Discovers a single ProcessPluginDefinition implementation with optional project root for enhanced class loading.
     *
     * <p>This method attempts to locate exactly one plugin definition using multiple discovery strategies.
     * If multiple plugins are found, preference is given to v2 implementations over v1. The discovery
     * process includes:</p>
     *
     * <ul>
     *   <li>Java ServiceLoader discovery using the current context class loader</li>
     *   <li>Manual scanning of JAR files in the classpath</li>
     *   <li>Directory scanning of common build output locations</li>
     *   <li>Enhanced project root scanning using {@link BpmnValidationUtils#createProjectClassLoader(File)}</li>
     * </ul>
     *
     * <p>Debug information is printed to System.out during the discovery process to aid in troubleshooting
     * plugin loading issues.</p>
     *
     * @param projectRoot optional project root directory for enhanced class loading (can be null)
     * @return the discovered plugin adapter, never null
     * @throws IllegalStateException if no plugin or multiple plugins of the same version are found
     */
    public static PluginAdapter discoverSingle(File projectRoot)
    {
        List<PluginAdapter> candidates = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = PluginDefinitionDiscovery.class.getClassLoader();

        // --- Step 1: Try with ServiceLoader (the standard way) ---
        try {
            Class<?> v2Class = Class.forName("dev.dsf.bpe.v2.ProcessPluginDefinition", false, cl);
            ServiceLoader<?> v2Loader = ServiceLoader.load(v2Class, cl);
            for (Object instance : v2Loader) candidates.add(new V2Adapter(instance));
        } catch (ClassNotFoundException ignored) {}
        try {
            Class<?> v1Class = Class.forName("dev.dsf.bpe.v1.ProcessPluginDefinition", false, cl);
            ServiceLoader<?> v1Loader = ServiceLoader.load(v1Class, cl);
            for (Object instance : v1Loader) candidates.add(new V1Adapter(instance));
        } catch (ClassNotFoundException ignored) {}

        // --- DEBUG: Report if found via ServiceLoader and show its root location ---
        if (!candidates.isEmpty()) {
            System.out.println("[DEBUG] Plugin found via Java ServiceLoader.");
            candidates.forEach(p -> {
                String location = "unknown";
                try {
                    // Get the JAR or directory path from where the class was loaded
                    location = p.sourceClass().getProtectionDomain().getCodeSource().getLocation().getPath();
                } catch (Exception ignored) {}
                System.out.println("  -> Found class: " + p.sourceClass().getName());
                System.out.println("     -> From root: " + location);
            });
        }

        // --- Step 2: If ServiceLoader failed, start manual scan ---
        if (candidates.isEmpty()) {
            System.out.println("[DEBUG] ServiceLoader found nothing. Starting manual scan...");
            candidates.addAll(scanJars(cl));
            candidates.addAll(scanDirectories(cl));
            if (projectRoot != null) {
                candidates.addAll(scanProjectRoot(projectRoot));
            }
        }

        // --- Step 3: Deduplicate and select the final candidate ---
        Set<String> seen = new LinkedHashSet<>();
        candidates.removeIf(a -> !seen.add(a.sourceClass().getName()));

        if (candidates.isEmpty()) throw new IllegalStateException("No ProcessPluginDefinition implementation found on classpath");

        List<PluginAdapter> v2 = candidates.stream().filter(a -> a instanceof V2Adapter).collect(Collectors.toList());
        List<PluginAdapter> pool = !v2.isEmpty() ? v2 : candidates;
        if (pool.size() > 1) {
            String api = !v2.isEmpty() ? "[v2]" : "[v1]";
            String names = pool.stream().map(a -> a.sourceClass().getName()).collect(Collectors.joining(", "));
            throw new IllegalStateException("Multiple ProcessPluginDefinition implementations found " + api + ": " + names);
        }

        PluginAdapter foundPlugin = pool.getFirst();
        System.out.println("[DEBUG] Final selected plugin: " + foundPlugin.sourceClass().getName());
        return foundPlugin;
    }

    /**
     * Scans the project root directory using the enhanced class loading strategy from BpmnValidationUtils.
     *
     * <p>This method provides comprehensive project-based scanning that covers cases like exploded JARs,
     * Maven/Gradle output directories, and dependency JARs. It first attempts ServiceLoader discovery
     * with an enhanced class loader, then falls back to direct class scanning if needed.</p>
     *
     * @param projectRoot the project root directory to scan
     * @return list of discovered plugin adapters, never null (empty list if none found)
     */
    private static List<PluginAdapter> scanProjectRoot(File projectRoot) {
        List<PluginAdapter> found = new ArrayList<>();

        try {
            ClassLoader projectCl = BpmnValidationUtils.createProjectClassLoader(projectRoot);

            // --- Step 1: Try ServiceLoader with the enhanced project class loader ---
            try {
                Class<?> v2Class = Class.forName("dev.dsf.bpe.v2.ProcessPluginDefinition", false, projectCl);
                ServiceLoader<?> v2Loader = ServiceLoader.load(v2Class, projectCl);
                for (Object instance : v2Loader) found.add(new V2Adapter(instance));
            } catch (ClassNotFoundException ignored) {}

            try {
                Class<?> v1Class = Class.forName("dev.dsf.bpe.v1.ProcessPluginDefinition", false, projectCl);
                ServiceLoader<?> v1Loader = ServiceLoader.load(v1Class, projectCl);
                for (Object instance : v1Loader) found.add(new V1Adapter(instance));
            } catch (ClassNotFoundException ignored) {}

            // --- DEBUG: Report if found via this enhanced ServiceLoader and show its root ---
            if (!found.isEmpty()) {
                System.out.println("[DEBUG] Plugin found via enhanced ServiceLoader (in project root scan).");
                found.forEach(p -> {
                    String location = "unknown";
                    try {
                        // Get the JAR or directory path from where the class was loaded
                        location = p.sourceClass().getProtectionDomain().getCodeSource().getLocation().getPath();
                    } catch (Exception ignored) {}
                    System.out.println("  -> Found class: " + p.sourceClass().getName());
                    System.out.println("     -> From root: " + location);
                });
            }

            // --- Step 2: If enhanced ServiceLoader failed, try direct class scanning ---
            if (found.isEmpty()) {
                found.addAll(scanProjectClassesDirectly(projectRoot, projectCl));
            }

        } catch (Exception e) {
            System.err.println("WARNING: Project root scanning failed: " + e.getMessage());
        }

        return found;
    }

    /**
     * Directly scans for ProcessPluginDefinition classes in the project using the project class loader.
     *
     * <p>This method handles cases where ServiceLoader registration might be missing but classes exist.
     * It scans typical build output directories and the project root for plugin implementations.</p>
     *
     * @param projectRoot the project root directory to scan
     * @param projectCl the project class loader to use for class loading
     * @return list of discovered plugin adapters, never null (empty list if none found)
     */
    private static List<PluginAdapter> scanProjectClassesDirectly(File projectRoot, ClassLoader projectCl) {
        List<PluginAdapter> found = new ArrayList<>();

        // Scan typical build output directories
        String[] buildPaths = {
            "target/classes",
            "build/classes/java/main",
            "build/classes",
            "out/production/classes"
        };

        for (String buildPath : buildPaths) {
            File buildDir = new File(projectRoot, buildPath);
            if (buildDir.exists() && buildDir.isDirectory()) {
                found.addAll(scanDirWithClassLoader(buildDir.toPath(), projectCl));
            }
        }

        // Also scan the project root directly (for exploded layouts)
        found.addAll(scanDirWithClassLoader(projectRoot.toPath(), projectCl));

        return found;
    }

    /**
     * Scans a directory for ProcessPluginDefinition classes using the specified class loader.
     *
     * @param root the root directory to scan
     * @param cl the class loader to use for loading discovered classes
     * @return list of discovered plugin adapters, never null (empty list if none found)
     */
    private static List<PluginAdapter> scanDirWithClassLoader(Path root, ClassLoader cl) {
        List<PluginAdapter> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            isProcessPluginDefinitionClassFile(root, cl, out, s);
        } catch (Exception ignored) {}
        return out;
    }

    /**
     * Processes a stream of paths to identify and validate ProcessPluginDefinition class files.
     *
     * <p>This method performs strict validation to ensure discovered classes both implement
     * the ProcessPluginDefinition interface and contain the required method signatures.
     * Debug information is printed for both successful discoveries and validation failures.</p>
     *
     * @param root the root path being scanned (used for debug output)
     * @param cl the class loader to use for loading classes
     * @param out the output list to add discovered plugin adapters to
     * @param s the stream of paths to process
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
     * Scans JAR files in the classpath for ProcessPluginDefinition implementations.
     *
     * <p>This method examines all JAR files found in the system classpath, looking for
     * classes that implement the ProcessPluginDefinition interface. Each JAR is loaded
     * with its own URLClassLoader to ensure proper isolation.</p>
     *
     * @param parentCl the parent class loader for JAR class loaders
     * @return list of discovered plugin adapters, never null (empty list if none found)
     * @throws UncheckedIOException if JAR file access fails
     */
    private static List<PluginAdapter> scanJars(ClassLoader parentCl) {
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
     * Scans common build output directories for ProcessPluginDefinition implementations.
     *
     * <p>This method checks standard build output locations used by Maven, Gradle, and IntelliJ IDEA
     * for compiled plugin classes.</p>
     *
     * @param parentCl the parent class loader for directory class loaders
     * @return list of discovered plugin adapters, never null (empty list if none found)
     */
    private static List<PluginAdapter> scanDirectories(ClassLoader parentCl) {
        List<PluginAdapter> found = new ArrayList<>();
        String[] scanPaths = { "target/classes", "build/classes/java/main", "out/production/classes" };
        for (String scanPath : scanPaths) {
            Path path = Paths.get(scanPath);
            if (Files.exists(path) && Files.isDirectory(path)) found.addAll(scanDir(path, parentCl));
        }
        return found;
    }

    /**
     * Scans a single directory for ProcessPluginDefinition implementations.
     *
     * @param root the root directory to scan
     * @param parentCl the parent class loader for the directory class loader
     * @return list of discovered plugin adapters, never null (empty list if none found)
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
     * Checks if a class implements any version of the ProcessPluginDefinition interface.
     *
     * @param c the class to check
     * @param cl the class loader to use for loading interface classes
     * @return true if the class implements ProcessPluginDefinition (v1 or v2), false otherwise
     */
    private static boolean implementsProcessPluginDefinition(Class<?> c, ClassLoader cl) {
        try { if (Class.forName("dev.dsf.bpe.v2.ProcessPluginDefinition", false, cl).isAssignableFrom(c)) return true; } catch (ClassNotFoundException ignored) {}
        try { if (Class.forName("dev.dsf.bpe.v1.ProcessPluginDefinition", false, cl).isAssignableFrom(c)) return true; } catch (ClassNotFoundException ignored) {}
        return false;
    }

    /**
     * Verifies that a class has the required method signatures for a ProcessPluginDefinition.
     *
     * @param c the class to check
     * @return true if all required methods are present, false otherwise
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
     * Converts a class file path to a fully qualified class name.
     *
     * @param root the root path containing the class file
     * @param clazz the path to the class file
     * @return the fully qualified class name
     */
    private static String toFqdn(Path root, Path clazz) {
        String rel = root.relativize(clazz).toString();
        return rel.substring(0, rel.length() - ".class".length()).replace(File.separatorChar, '.');
    }

    /**
     * Checks if a class is assignable to the v2 ProcessPluginDefinition interface.
     *
     * @param c the class to check
     * @param cl the class loader to use for loading the v2 interface
     * @return true if the class implements the v2 interface, false otherwise
     */
    private static boolean isAssignableTo(Class<?> c, ClassLoader cl) {
        try {
            return Class.forName("dev.dsf.bpe.v2.ProcessPluginDefinition", false, cl).isAssignableFrom(c);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
