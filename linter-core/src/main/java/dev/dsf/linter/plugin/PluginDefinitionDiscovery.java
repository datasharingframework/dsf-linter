package dev.dsf.linter.plugin;

import dev.dsf.linter.logger.LogUtils;
import dev.dsf.linter.util.loader.ClassLoaderUtils;
import dev.dsf.linter.util.linting.PluginLintingUtils;
import dev.dsf.linter.util.loader.ServiceLoaderUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.dsf.linter.classloading.ClassInspector.logger;
import static dev.dsf.linter.classloading.ProjectClassLoaderFactory.getOrCreateRecursiveProjectClassLoader;

/**
 * Discovers and loads ProcessPluginDefinition implementations from extracted JAR structures.
 * Supports both v1 and v2 plugin APIs using ServiceLoader and direct class scanning fallback.
 * <p>
 * Discovery strategy for extracted JARs:
 * <ol>
 *   <li>Try ServiceLoader discovery using META-INF/services/</li>
 *   <li>If nothing found, scan the project root directory directly for plugin classes</li>
 * </ol>
 * </p>
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
        private final Object delegate;
        private final Class<?> delegateClass;

        public V1Adapter(Object delegate) {
            this.delegate = delegate;
            this.delegateClass = delegate.getClass();
        }

        @Override
        public String getName() {
            try {
                String r = (String) delegateClass.getMethod("getName").invoke(delegate);
                return r != null ? r : "";
            } catch (Exception e) {
                throw new RuntimeException("getName", e);
            }
        }

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

        @Override
        public Class<?> sourceClass() {
            return delegateClass;
        }
    }

    /**
     * Adapter for v2 ProcessPluginDefinition implementations.
     */
    public static final class V2Adapter implements PluginAdapter {
        private final Object delegate;
        private final Class<?> delegateClass;

        public V2Adapter(Object delegate) {
            this.delegate = delegate;
            this.delegateClass = delegate.getClass();
        }

        @Override
        public String getName() {
            try {
                String r = (String) delegateClass.getMethod("getName").invoke(delegate);
                return r != null ? r : "";
            } catch (Exception e) {
                throw new RuntimeException("getName", e);
            }
        }

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

        @Override
        public Class<?> sourceClass() {
            return delegateClass;
        }
    }

    /**
     * Scans the project root for plugin definitions using a recursive project ClassLoader.
     * <p>
     * For extracted JAR structures, this method:
     * <ol>
     *   <li>First tries ServiceLoader discovery (reads META-INF/services/)</li>
     *   <li>If nothing found, scans the project root directory directly</li>
     * </ol>
     * </p>
     *
     * @param projectRoot the project root directory (extracted JAR root)
     * @param context discovery context to collect results and errors
     */
    static void scanProjectRoot(File projectRoot, DiscoveryContext context) {
        try {
            ClassLoader projectCl = getOrCreateRecursiveProjectClassLoader(projectRoot);

            ClassLoaderUtils.withTemporaryContextClassLoader(projectCl, () -> {
                logger.debug("DEBUG: Attempting ServiceLoader discovery for extracted JAR...");

                List<PluginAdapter> found = new ArrayList<>(ServiceLoaderUtils.discoverPluginsViaServiceLoader(projectCl));

                if (!found.isEmpty()) {
                    logger.debug("DEBUG: SUCCESS - Plugins found via ServiceLoader.");
                    found.forEach(context::addSuccess);
                } else {
                    logger.debug("DEBUG: ServiceLoader found nothing. Starting direct scan of project root...");
                    scanProjectRootDirectly(projectRoot, projectCl, context);
                }
            });
        } catch (Exception e) {
            LogUtils.logAndRethrow(logger, "Plugin discovery failed", e);
        }
    }

    /**
     * Scans the project root directory directly for plugin classes.
     * <p>
     * This is used as a fallback when ServiceLoader discovery finds nothing.
     * For extracted JARs, classes are typically in a flat structure at the root level.
     * </p>
     *
     * @param projectRoot the project root directory
     * @param projectCl   the project class loader
     * @param context discovery context to collect results and errors
     */
    private static void scanProjectRootDirectly(File projectRoot, ClassLoader projectCl, DiscoveryContext context) {
        Path rootPath = projectRoot.toPath();
        logger.debug("DEBUG: Scanning project root directory: " + rootPath);

        try (Stream<Path> s = Files.walk(rootPath)) {
            processPluginClassFiles(rootPath, projectCl, context, s);
        } catch (IOException e) {
            logger.debug("WARNING: Failed to scan project root: " + e.getMessage());
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
     * Filters and validates ProcessPluginDefinition class files from stream.
     * Performs comprehensive validation and error collection.
     *
     * @param root the root path for class loading
     * @param cl   class loader for validation
     * @param context  discovery context to collect results and errors
     * @param s    stream of file paths to process
     */
    private static void processPluginClassFiles(Path root, ClassLoader cl, DiscoveryContext context, Stream<Path> s) {
        s.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith("ProcessPluginDefinition.class"))
                .filter(p -> !p.getFileName().toString().contains("$"))
                .forEach(p -> {
                    String fqdn = toFqdn(root, p);
                    String location = root.toAbsolutePath().toString();

                    try {
                        Class<?> c = Class.forName(fqdn, false, cl);
                        validateAndRegisterPlugin(c, location, context);

                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        logger.debug("Failed to load class: " + fqdn + " - " + e.getMessage());
                        context.addFailure(PluginDiscoveryError.classLoadingFailed(fqdn, location, e));

                    } catch (Exception e) {
                        logger.debug("Unexpected error loading class " + fqdn + ": " + e.getMessage());
                    }
                });
    }

    /**
     * Validates a plugin class and registers it if valid, or records errors if invalid.
     *
     * @param c the class to validate
     * @param location the location where the class was found
     * @param context the discovery context to collect results and errors
     */
    private static void validateAndRegisterPlugin(Class<?> c, String location, DiscoveryContext context) {
        String className = c.getName();

        boolean implementsInterface = PluginLintingUtils.implementsProcessPluginDefinition(c, c.getClassLoader());
        if (!implementsInterface) {
            logger.debug("DEBUG: FAILED - Candidate class does not implement the ProcessPluginDefinition interface.");
            logger.debug("  - Candidate class: " + className);
            logger.debug("  - From root: " + location);
            return;
        }

        boolean hasRequiredMethods = PluginLintingUtils.hasPluginSignature(c);
        if (!hasRequiredMethods) {
            logger.debug("DEBUG: FAILED - Class implements interface but is missing required methods.");
            logger.debug("  - Candidate class: " + className);
            logger.debug("  - From root: " + location);
            context.addFailure(PluginDiscoveryError.missingMethods(className, location));
            return;
        }

        Object instance;
        try {
            instance = c.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            logger.debug("DEBUG: FAILED - Cannot instantiate plugin class: " + e.getMessage());
            logger.debug("  - Candidate class: " + className);
            logger.debug("  - From root: " + location);
            context.addFailure(PluginDiscoveryError.instantiationFailed(className, location, e));
            return;
        }

        boolean isV2 = PluginLintingUtils.isV2Plugin(c, c.getClassLoader());
        if (isV2) {
            logger.debug("DEBUG: SUCCESS - Found valid V2 plugin definition.");
            logger.debug("  - Found class: " + className);
            logger.debug("  - From root: " + location);
            context.addSuccess(new V2Adapter(instance));
            return;
        }

        boolean isV1 = PluginLintingUtils.isV1Plugin(c, c.getClassLoader());
        if (isV1) {
            logger.debug("DEBUG: SUCCESS - Found valid V1 plugin definition.");
            logger.debug("  - Found class: " + className);
            logger.debug("  - From root: " + location);
            context.addSuccess(new V1Adapter(instance));
            return;
        }

        logger.error("✗ Plugin discovery failed: " + className);
        logger.error("  Reason: Does not implement valid DSF API interface (neither v1 nor v2)");
        logger.error("  Location: " + location);
        context.addFailure(PluginDiscoveryError.invalidApiVersion(className, location));
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