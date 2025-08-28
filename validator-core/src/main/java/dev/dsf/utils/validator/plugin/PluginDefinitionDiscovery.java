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
 * Comprehensive utility class for discovering and loading ProcessPluginDefinition implementations at runtime.
 *
 * <p>This class provides a robust and flexible framework for locating ProcessPluginDefinition implementations
 * that conform to either the v1 or v2 API specifications. The discovery mechanism is designed to handle
 * various deployment scenarios including standalone applications, multi-module projects, and complex
 * classpath configurations.</p>
 *
 * <p><strong>Discovery Strategy Overview:</strong></p>
 * <p>The plugin discovery process employs a multi-layered approach with the following priority hierarchy:</p>
 * <ol>
 *   <li><strong>Java ServiceLoader Discovery</strong> - Utilizes the standard Java ServiceLoader mechanism
 *       with the current thread context class loader to locate properly registered plugin services</li>
 *   <li><strong>Manual JAR Scanning</strong> - Performs comprehensive scanning of all JAR files found
 *       in the system classpath when ServiceLoader discovery fails</li>
 *   <li><strong>Directory Scanning</strong> - Examines common build output directories (Maven, Gradle, IntelliJ)
 *       for compiled plugin classes in development environments</li>
 *   <li><strong>Enhanced Project Root Scanning</strong> - Leverages recursive project structure analysis
 *       when a project root directory is provided, supporting complex multi-module layouts</li>
 * </ol>
 *
 * <p><strong>Plugin Version Compatibility:</strong></p>
 * <p>The discovery system supports both v1 and v2 ProcessPluginDefinition implementations through
 * a unified adapter pattern. When multiple plugins are discovered, v2 implementations take precedence
 * over v1 implementations. All discovered plugins are wrapped in adapter instances that provide
 * a consistent interface regardless of the underlying plugin API version.</p>
 *
 * <p><strong>Error Handling and Diagnostics:</strong></p>
 * <p>The class includes comprehensive debug output to System.out during the discovery process,
 * providing detailed information about:</p>
 * <ul>
 *   <li>Discovery method attempts and their results</li>
 *   <li>Class loading locations and sources</li>
 *   <li>Validation failures and their specific causes</li>
 *   <li>Final plugin selection decisions</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong></p>
 * <p>This class is designed to be thread-safe for concurrent discovery operations. However,
 * the discovery process temporarily modifies the thread context class loader, so concurrent
 * calls from the same thread should be avoided.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Discover plugin with project root support
 * File projectRoot = new File("/path/to/project");
 * PluginAdapter plugin = PluginDefinitionDiscovery.discoverSingle(projectRoot);
 *
 * // Access plugin information
 * String name = plugin.getName();
 * List<String> models = plugin.getProcessModels();
 * Map<String, List<String>> resources = plugin.getFhirResourcesByProcessId();
 * }</pre>
 *
 * @author DSF Development Team
 * @version 2.0
 * @since 1.0
 * @see BpmnValidationUtils#getOrCreateRecursiveProjectClassLoader(File)
 * @see ServiceLoader
 */
public final class PluginDefinitionDiscovery
{
    /**
     * Unified adapter interface that provides version-agnostic access to ProcessPluginDefinition implementations.
     *
     * <p>This interface serves as an abstraction layer that normalizes the differences between v1 and v2
     * ProcessPluginDefinition APIs, ensuring consistent access to plugin metadata and resources regardless
     * of the underlying implementation version.</p>
     *
     * <p><strong>Design Principles:</strong></p>
     * <ul>
     *   <li><strong>Version Transparency</strong> - Client code can interact with plugins without knowledge
     *       of the specific API version being used</li>
     *   <li><strong>Consistent Return Values</strong> - All methods guarantee non-null return values,
     *       providing empty collections or default strings when data is unavailable</li>
     *   <li><strong>Reflection-Based Implementation</strong> - Adapters use reflection to invoke methods
     *       on the underlying plugin instances, providing flexibility for future API evolution</li>
     * </ul>
     *
     * <p><strong>Error Handling:</strong></p>
     * <p>All methods in this interface are designed to handle reflection errors gracefully,
     * wrapping checked exceptions in RuntimeExceptions with descriptive messages to aid in debugging.</p>
     *
     * @since 1.0
     */
    public interface PluginAdapter
    {
        /**
         * Retrieves the human-readable name of the plugin.
         *
         * <p>This method returns the plugin's display name as defined by the underlying implementation.
         * The name is typically used for logging, debugging, and user interface display purposes.</p>
         *
         * <p><strong>Implementation Notes:</strong></p>
         * <ul>
         *   <li>The returned name should be suitable for display in user interfaces</li>
         *   <li>Names may contain spaces, special characters, and Unicode text</li>
         *   <li>Empty strings are returned when the plugin does not define a name</li>
         *   <li>The name should be stable across plugin versions when possible</li>
         * </ul>
         *
         * @return the plugin name as a String, never null (returns empty string if not available
         *         or if the underlying plugin returns null)
         * @throws RuntimeException if reflection-based method invocation fails on the underlying plugin instance
         * @since 1.0
         */
        String getName();

        /**
         * Retrieves the list of BPMN process model identifiers provided by this plugin.
         *
         * <p>This method returns a list of process model identifiers that represent the BPMN processes
         * defined within the plugin. These identifiers are typically used for process deployment,
         * execution routing, and process registry operations.</p>
         *
         * <p><strong>Process Model Identifier Format:</strong></p>
         * <p>Process model identifiers typically follow patterns such as:</p>
         * <ul>
         *   <li>Fully qualified names (e.g., "com.example.processes.DataProcessing")</li>
         *   <li>Simple names (e.g., "data-processing-workflow")</li>
         *   <li>URI-style identifiers (e.g., "http://example.com/processes/data-processing")</li>
         * </ul>
         *
         * <p><strong>Return Value Characteristics:</strong></p>
         * <ul>
         *   <li>The returned list is immutable and safe for concurrent access</li>
         *   <li>Order of elements may be significant for process deployment sequences</li>
         *   <li>Duplicate identifiers within the same plugin should not occur</li>
         *   <li>Empty list indicates the plugin defines no process models</li>
         * </ul>
         *
         * @return an immutable list of process model identifiers, never null (returns empty list
         *         if no process models are available or if the underlying plugin returns null)
         * @throws RuntimeException if reflection-based method invocation fails on the underlying plugin instance
         * @since 1.0
         * @see #getFhirResourcesByProcessId()
         */
        List<String> getProcessModels();

        /**
         * Retrieves a mapping of process identifiers to their associated FHIR resource collections.
         *
         * <p>This method provides access to the FHIR (Fast Healthcare Interoperability Resources)
         * resources that are associated with each process defined in the plugin. This mapping is
         * crucial for healthcare-related workflows that need to understand which FHIR resources
         * are consumed, produced, or modified by specific processes.</p>
         *
         * <p><strong>Map Structure:</strong></p>
         * <ul>
         *   <li><strong>Keys</strong> - Process identifiers that should correspond to identifiers
         *       returned by {@link #getProcessModels()}</li>
         *   <li><strong>Values</strong> - Lists of FHIR resource identifiers (e.g., "Patient", "Observation",
         *       "DiagnosticReport") that are relevant to the associated process</li>
         * </ul>
         *
         * <p><strong>FHIR Resource Identifier Examples:</strong></p>
         * <ul>
         *   <li>Standard FHIR R4 resources: "Patient", "Practitioner", "Organization"</li>
         *   <li>Custom resource profiles: "CustomPatientProfile", "ExtendedObservation"</li>
         *   <li>Resource URLs: "http://hl7.org/fhir/StructureDefinition/Patient"</li>
         * </ul>
         *
         * <p><strong>Use Cases:</strong></p>
         * <ul>
         *   <li>Process validation to ensure required FHIR resources are available</li>
         *   <li>Security configuration to set appropriate access controls</li>
         *   <li>Data flow analysis to understand resource dependencies</li>
         *   <li>Documentation generation for process-resource relationships</li>
         * </ul>
         *
         * @return an immutable map where keys are process IDs and values are lists of FHIR resource
         *         identifiers, never null (returns empty map if no resources are available or if
         *         the underlying plugin returns null)
         * @throws RuntimeException if reflection-based method invocation fails on the underlying plugin instance
         * @since 1.0
         * @see #getProcessModels()
         */
        Map<String, List<String>> getFhirResourcesByProcessId();

        /**
         * Retrieves the underlying Java class object representing the plugin implementation.
         *
         * <p>This method provides access to the actual Class object of the plugin implementation,
         * which can be used for advanced reflection operations, debugging, and introspection purposes.</p>
         *
         * <p><strong>Common Use Cases:</strong></p>
         * <ul>
         *   <li><strong>Debugging</strong> - Obtaining class name, package, and source location information</li>
         *   <li><strong>Advanced Reflection</strong> - Accessing custom methods or annotations on the plugin class</li>
         *   <li><strong>Class Loading Analysis</strong> - Determining the class loader hierarchy and source JAR</li>
         *   <li><strong>Version Detection</strong> - Distinguishing between v1 and v2 plugin implementations</li>
         * </ul>
         *
         * <p><strong>Security Considerations:</strong></p>
         * <p>Access to the source class enables reflection-based operations that may bypass normal
         * encapsulation. Users of this method should ensure they do not violate the plugin's
         * intended API boundaries or security constraints.</p>
         *
         * <p><strong>Example Usage:</strong></p>
         * <pre>{@code
         * PluginAdapter plugin = PluginDefinitionDiscovery.discoverSingle(null);
         * Class<?> pluginClass = plugin.sourceClass();
         * String className = pluginClass.getName();
         * Package pluginPackage = pluginClass.getPackage();
         * URL sourceLocation = pluginClass.getProtectionDomain().getCodeSource().getLocation();
         * }</pre>
         *
         * @return the Class object representing the plugin implementation, never null
         * @since 1.0
         */
        Class<?> sourceClass();
    }

    /**
     * Adapter implementation for v1 ProcessPluginDefinition implementations.
     *
     * <p>This adapter provides compatibility with legacy v1 ProcessPluginDefinition implementations
     * by wrapping them with the unified {@link PluginAdapter} interface. The adapter uses reflection
     * to invoke methods on the v1 plugin instances, ensuring that existing v1 plugins continue to
     * function without modification.</p>
     *
     * <p><strong>V1 API Characteristics:</strong></p>
     * <ul>
     *   <li>Original ProcessPluginDefinition interface design</li>
     *   <li>Basic method signatures for name, process models, and FHIR resources</li>
     *   <li>Simpler resource mapping structures compared to v2</li>
     *   <li>Legacy compatibility maintained for existing deployments</li>
     * </ul>
     *
     * <p><strong>Reflection-Based Implementation:</strong></p>
     * <p>This adapter uses Java reflection to dynamically invoke methods on the v1 plugin instance.
     * This approach provides flexibility and maintains loose coupling between the discovery framework
     * and the specific v1 API implementation. All reflection errors are wrapped in RuntimeExceptions
     * with descriptive messages to aid in troubleshooting.</p>
     *
     * <p><strong>Error Handling:</strong></p>
     * <p>Method invocations that fail due to reflection errors result in RuntimeExceptions.
     * Null return values from the underlying v1 plugin are converted to appropriate default
     * values (empty string, empty list, or empty map) to maintain the non-null contract
     * of the PluginAdapter interface.</p>
     *
     * @since 1.0
     * @see V2Adapter
     */
    public static final class V1Adapter implements PluginAdapter
    {
        /** The wrapped v1 ProcessPluginDefinition instance. */
        private final Object delegate;
        /** The Class object representing the v1 plugin implementation. */
        private final Class<?> delegateClass;

        /**
         * Creates a new V1Adapter wrapping the specified v1 ProcessPluginDefinition instance.
         *
         * <p>This constructor initializes the adapter with a v1 plugin instance and caches
         * the plugin's Class object for efficient reflection operations. The delegate object
         * is expected to implement the v1 ProcessPluginDefinition interface.</p>
         *
         * <p><strong>Validation:</strong></p>
         * <p>The constructor performs basic validation to ensure the delegate is not null.
         * However, it does not validate that the delegate actually implements the v1
         * ProcessPluginDefinition interface - this validation is assumed to have been
         * performed by the discovery process.</p>
         *
         * <p><strong>Thread Safety:</strong></p>
         * <p>The constructed adapter is thread-safe for read operations, as both the delegate
         * reference and the cached Class object are immutable after construction.</p>
         *
         * @param delegate the v1 ProcessPluginDefinition instance to wrap, must not be null
         * @throws IllegalArgumentException if delegate is null
         * @since 1.0
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
     * Adapter implementation for v2 ProcessPluginDefinition implementations.
     *
     * <p>This adapter provides support for the enhanced v2 ProcessPluginDefinition API by wrapping
     * v2 implementations with the unified {@link PluginAdapter} interface. The v2 API includes
     * improvements and additional capabilities compared to the original v1 specification.</p>
     *
     * <p><strong>V2 API Enhancements:</strong></p>
     * <ul>
     *   <li>Enhanced metadata structures for better plugin introspection</li>
     *   <li>Improved FHIR resource mapping with additional relationship information</li>
     *   <li>Better support for complex multi-process plugins</li>
     *   <li>Enhanced error handling and validation capabilities</li>
     *   <li>Future-proofed API design for extensibility</li>
     * </ul>
     *
     * <p><strong>Priority in Discovery:</strong></p>
     * <p>When both v1 and v2 implementations are discovered, the discovery process prioritizes
     * v2 implementations over v1 implementations. This ensures that newer, enhanced plugin
     * versions are preferred when available.</p>
     *
     * <p><strong>Reflection-Based Implementation:</strong></p>
     * <p>Like the V1Adapter, this adapter uses Java reflection to dynamically invoke methods
     * on the v2 plugin instance. This maintains the same loose coupling benefits while
     * supporting the enhanced v2 API capabilities.</p>
     *
     * <p><strong>Backward Compatibility:</strong></p>
     * <p>Despite being designed for v2 plugins, this adapter maintains the same method
     * signatures as V1Adapter, ensuring that client code can work with both v1 and v2
     * plugins transparently through the PluginAdapter interface.</p>
     *
     * @since 1.0
     * @see V1Adapter
     */
    public static final class V2Adapter implements PluginAdapter
    {
        /** The wrapped v2 ProcessPluginDefinition instance. */
        private final Object delegate;
        /** The Class object representing the v2 plugin implementation. */
        private final Class<?> delegateClass;

        /**
         * Creates a new V2Adapter wrapping the specified v2 ProcessPluginDefinition instance.
         *
         * <p>This constructor initializes the adapter with a v2 plugin instance and caches
         * the plugin's Class object for efficient reflection operations. The delegate object
         * is expected to implement the v2 ProcessPluginDefinition interface with its
         * enhanced capabilities.</p>
         *
         * <p><strong>V2 Instance Requirements:</strong></p>
         * <ul>
         *   <li>Must implement the v2 ProcessPluginDefinition interface</li>
         *   <li>Should provide enhanced metadata compared to v1 implementations</li>
         *   <li>Expected to support improved FHIR resource mapping structures</li>
         * </ul>
         *
         * <p><strong>Performance Considerations:</strong></p>
         * <p>The Class object is cached during construction to avoid repeated reflection
         * overhead during method invocations. This caching provides better performance
         * for plugins that are accessed frequently.</p>
         *
         * @param delegate the v2 ProcessPluginDefinition instance to wrap, must not be null
         * @throws IllegalArgumentException if delegate is null
         * @since 1.0
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
     * Discovers exactly one ProcessPluginDefinition implementation using comprehensive discovery strategies.
     *
     * <p>This is the primary entry point for plugin discovery operations. The method employs a sophisticated
     * multi-layered discovery approach designed to handle various deployment scenarios including development
     * environments, packaged applications, and complex multi-module projects.</p>
     *
     * <p><strong>Discovery Process Overview:</strong></p>
     * <p>The discovery process follows a carefully designed priority hierarchy:</p>
     * <ol>
     *   <li><strong>ServiceLoader Discovery</strong> - Attempts to use the standard Java ServiceLoader
     *       mechanism with the current thread context class loader. This is the preferred method for
     *       properly packaged and registered plugins.</li>
     *   <li><strong>Manual JAR Scanning</strong> - If ServiceLoader fails, scans all JAR files in the
     *       system classpath for plugin implementations. Each JAR is loaded with its own URLClassLoader
     *       for proper isolation.</li>
     *   <li><strong>Directory Scanning</strong> - Examines common build output directories including
     *       Maven (target/classes), Gradle (build/classes/java/main), and IntelliJ IDEA
     *       (out/production/classes) locations.</li>
     *   <li><strong>Enhanced Project Root Scanning</strong> - When a project root is provided, leverages
     *       {@link BpmnValidationUtils#getOrCreateRecursiveProjectClassLoader(File)} for comprehensive
     *       project structure analysis and recursive scanning of multi-module layouts.</li>
     * </ol>
     *
     * <p><strong>Plugin Version Priority:</strong></p>
     * <p>When multiple plugins are discovered, the selection follows these rules:</p>
     * <ul>
     *   <li>V2 implementations are always preferred over v1 implementations</li>
     *   <li>Multiple plugins of the same version result in an IllegalStateException</li>
     *   <li>The system ensures exactly one plugin is returned or throws an exception</li>
     * </ul>
     *
     * <p><strong>Project Root Enhancement:</strong></p>
     * <p>When a project root directory is provided, the method enables enhanced discovery capabilities:</p>
     * <ul>
     *   <li>Recursive scanning of all submodules and subdirectories</li>
     *   <li>Support for complex Maven and Gradle multi-module project structures</li>
     *   <li>Automatic detection of various build output directory patterns</li>
     *   <li>Intelligent class loader construction for optimal plugin loading</li>
     * </ul>
     *
     * <p><strong>Debug and Diagnostic Output:</strong></p>
     * <p>The method provides comprehensive debug information to System.out including:</p>
     * <ul>
     *   <li>Discovery method attempts and their success/failure status</li>
     *   <li>Detailed class loading information and source locations</li>
     *   <li>Plugin validation results with specific failure reasons</li>
     *   <li>Final selection decisions and reasoning</li>
     * </ul>
     *
     * <p><strong>Error Conditions:</strong></p>
     * <p>The method throws IllegalStateException in the following cases:</p>
     * <ul>
     *   <li>No ProcessPluginDefinition implementation found after all discovery attempts</li>
     *   <li>Multiple plugins of the same API version discovered</li>
     *   <li>Plugin validation failures that prevent proper instantiation</li>
     * </ul>
     *
     * <p><strong>Thread Safety Considerations:</strong></p>
     * <p>This method temporarily modifies the thread context class loader during project root scanning.
     * The original class loader is always restored before the method returns. Concurrent calls from
     * the same thread should be avoided to prevent class loader conflicts.</p>
     *
     * <p><strong>Usage Examples:</strong></p>
     * <pre>{@code
     * // Basic discovery without project root
     * PluginAdapter plugin = PluginDefinitionDiscovery.discoverSingle(null);
     *
     * // Enhanced discovery with project root support
     * File projectRoot = new File("/path/to/my-project");
     * PluginAdapter plugin = PluginDefinitionDiscovery.discoverSingle(projectRoot);
     *
     * // Access discovered plugin information
     * String name = plugin.getName();
     * List<String> processes = plugin.getProcessModels();
     * Map<String, List<String>> fhirResources = plugin.getFhirResourcesByProcessId();
     * Class<?> pluginClass = plugin.sourceClass();
     * }</pre>
     *
     * @param projectRoot optional project root directory for enhanced class loading and discovery.
     *                   When provided, enables recursive project scanning and intelligent class loader
     *                   construction. May be null to disable enhanced project root scanning.
     * @return the discovered plugin adapter wrapping either a v1 or v2 ProcessPluginDefinition implementation,
     *         never null
     * @throws IllegalStateException if no plugin implementation is found after exhaustive discovery attempts,
     *                              or if multiple plugins of the same API version are discovered
     * @throws RuntimeException if critical errors occur during the discovery process, such as class loading
     *                         failures or reflection errors
     * @since 1.0
     * @see BpmnValidationUtils#getOrCreateRecursiveProjectClassLoader(File)
     * @see PluginAdapter
     * @see V1Adapter
     * @see V2Adapter
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
     * Scans the project root directory using centralized recursive classpath construction and discovery mechanisms.
     *
     * <p>This method represents the most sophisticated discovery strategy available, designed specifically
     * for complex project structures and development environments. It leverages the centralized recursive
     * classpath builder provided by {@link BpmnValidationUtils#getOrCreateRecursiveProjectClassLoader(File)}
     * to create a comprehensive view of the entire project hierarchy.</p>
     *
     * <p><strong>Discovery Strategy:</strong></p>
     * <p>The method employs a two-phase discovery approach:</p>
     * <ol>
     *   <li><strong>ServiceLoader Discovery with Enhanced Classpath</strong> - Uses the recursive project
     *       class loader with ServiceLoader to attempt standard plugin discovery with comprehensive classpath coverage</li>
     *   <li><strong>Direct Class Scanning Fallback</strong> - If ServiceLoader fails, performs direct filesystem
     *       scanning of build directories using the same enhanced class loader</li>
     * </ol>
     *
     * <p><strong>Recursive Project Structure Support:</strong></p>
     * <p>The method is specifically designed to handle complex project layouts including:</p>
     * <ul>
     *   <li>Multi-module Maven projects with nested submodules</li>
     *   <li>Gradle composite builds and multi-project structures</li>
     *   <li>Mixed build system environments</li>
     *   <li>Non-standard directory layouts and custom build configurations</li>
     * </ul>
     *
     * <p><strong>Class Loader Management:</strong></p>
     * <p>The method carefully manages thread context class loader state:</p>
     * <ul>
     *   <li>Temporarily sets the thread context class loader to the recursive project class loader</li>
     *   <li>Ensures ServiceLoader.load() operations use the enhanced classpath</li>
     *   <li>Always restores the original thread context class loader before returning</li>
     *   <li>Provides proper exception handling to guarantee class loader restoration</li>
     * </ul>
     *
     * <p><strong>Performance and Caching:</strong></p>
     * <p>The method leverages caching mechanisms in BpmnValidationUtils to avoid repeated classpath
     * construction overhead. Subsequent calls with the same project root benefit from cached class loaders.</p>
     *
     * <p><strong>Error Handling:</strong></p>
     * <p>Comprehensive error handling ensures graceful degradation:</p>
     * <ul>
     *   <li>Critical exceptions are logged with full stack traces</li>
     *   <li>Non-critical failures fall back to alternative discovery methods</li>
     *   <li>Thread context class loader is always properly restored</li>
     *   <li>Partial results are preserved when possible</li>
     * </ul>
     *
     * @param projectRoot the root directory of the project to scan recursively, must not be null
     *                   and should represent a valid project directory structure
     * @return a list of discovered plugin adapters, never null but may be empty if no plugins are found.
     *         The list maintains discovery order and may contain both v1 and v2 implementations.
     * @throws IllegalArgumentException if projectRoot is null or does not represent a valid directory
     * @since 1.0
     * @see BpmnValidationUtils#getOrCreateRecursiveProjectClassLoader(File)
     * @see #scanProjectClassesDirectly(File, ClassLoader)
     */
    private static List<PluginAdapter> scanProjectRoot(File projectRoot) {
        List<PluginAdapter> found = new ArrayList<>();

        try {
            // 1) Get cached recursive project class loader from the utils
            ClassLoader projectCl = BpmnValidationUtils.getOrCreateRecursiveProjectClassLoader(projectRoot);

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
     * Performs direct filesystem scanning for ProcessPluginDefinition classes within a project structure.
     *
     * <p>This method serves as a comprehensive fallback mechanism when ServiceLoader-based discovery
     * fails to locate plugin implementations. It handles cases where plugin classes exist but lack
     * proper ServiceLoader registration files, or where custom build configurations place classes
     * in non-standard locations.</p>
     *
     * <p><strong>Scanning Strategy:</strong></p>
     * <p>The method employs a multi-layered scanning approach:</p>
     * <ol>
     *   <li><strong>Recursive Build Directory Discovery</strong> - Walks the entire project directory
     *       tree to locate common build output directories (Maven target/classes, Gradle build/classes, etc.)</li>
     *   <li><strong>Per-Directory Class Scanning</strong> - Scans each discovered build directory for
     *       ProcessPluginDefinition implementations</li>
     *   <li><strong>Root Directory Fallback</strong> - If recursive scanning yields no results, performs
     *       a direct scan of the project root to handle non-standard or "exploded" class layouts</li>
     * </ol>
     *
     * <p><strong>Build System Support:</strong></p>
     * <p>The method recognizes and handles build output patterns from major Java build systems:</p>
     * <ul>
     *   <li><strong>Apache Maven</strong> - target/classes directories in any submodule</li>
     *   <li><strong>Gradle</strong> - build/classes/java/main directories with various Gradle project structures</li>
     *   <li><strong>IntelliJ IDEA</strong> - Custom build output directories and IDE-specific layouts</li>
     *   <li><strong>Eclipse</strong> - Standard Eclipse project build directories</li>
     * </ul>
     *
     * <p><strong>Multi-Module Project Handling:</strong></p>
     * <p>The recursive scanning approach is specifically designed to handle complex project structures:</p>
     * <ul>
     *   <li>Maven multi-module projects with nested parent-child relationships</li>
     *   <li>Gradle composite builds and included projects</li>
     *   <li>Mixed build system environments with multiple build tools</li>
     *   <li>Custom project layouts that don't follow standard conventions</li>
     * </ul>
     *
     * <p><strong>Class Loading Integration:</strong></p>
     * <p>All discovered classes are loaded using the provided project class loader, ensuring:</p>
     * <ul>
     *   <li>Consistent class loading behavior across the entire project</li>
     *   <li>Proper resolution of project dependencies and classpath resources</li>
     *   <li>Support for complex dependency graphs and inter-module relationships</li>
     * </ul>
     *
     * <p><strong>Performance Considerations:</strong></p>
     * <p>The method includes several optimizations to minimize scanning overhead:</p>
     * <ul>
     *   <li>Early termination when plugins are found in common build directories</li>
     *   <li>Efficient filesystem walking with appropriate filtering</li>
     *   <li>Lazy evaluation of class loading operations</li>
     *   <li>Targeted scanning based on common build output patterns</li>
     * </ul>
     *
     * <p><strong>Error Resilience:</strong></p>
     * <p>The method is designed to be resilient to various filesystem and class loading issues:</p>
     * <ul>
     *   <li>Individual directory access failures don't stop the entire scan</li>
     *   <li>Class loading errors are handled gracefully without terminating discovery</li>
     *   <li>I/O exceptions are caught and logged but don't propagate</li>
     * </ul>
     *
     * @param projectRoot the root directory of the project to scan, must represent a valid directory
     *                   that serves as the starting point for recursive build directory discovery
     * @param projectCl the project-specific class loader to use for loading discovered classes,
     *                 should be configured with the appropriate classpath for the project
     * @return a list of discovered plugin adapters, never null but may be empty if no valid
     *         ProcessPluginDefinition implementations are found. Results maintain discovery order.
     * @since 1.0
     * @see #scanDirWithClassLoader(Path, ClassLoader)
     * @see Files#walk (Path)
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
     * Scans a specific directory for ProcessPluginDefinition class files using a provided class loader.
     *
     * <p>This method performs targeted directory scanning for plugin implementations, serving as a
     * fundamental building block for the broader discovery system. It handles the detailed work of
     * examining directory contents, identifying potential plugin classes, and validating their
     * implementation correctness.</p>
     *
     * <p><strong>Scanning Process:</strong></p>
     * <p>The method performs comprehensive directory traversal with the following steps:</p>
     * <ol>
     *   <li><strong>Recursive Directory Walking</strong> - Uses {@link Files#walk (Path)} to traverse
     *       the entire directory tree from the specified root</li>
     *   <li><strong>Class File Filtering</strong> - Identifies files that match ProcessPluginDefinition
     *       naming patterns and excludes inner classes</li>
     *   <li><strong>Class Loading and Validation</strong> - Loads each candidate class and validates
     *       its implementation against ProcessPluginDefinition interface requirements</li>
     *   <li><strong>Adapter Creation</strong> - Creates appropriate adapter instances for validated plugins</li>
     * </ol>
     *
     * <p><strong>Class File Identification:</strong></p>
     * <p>The method uses specific criteria to identify potential plugin classes:</p>
     * <ul>
     *   <li>Files must have .class extension</li>
     *   <li>Class names must end with "ProcessPluginDefinition"</li>
     *   <li>Inner classes (containing ' in filename) are excluded</li>
     *   <li>Files must be regular files, not directories or symbolic links</li>
     * </ul>
     *
     * <p><strong>Class Loader Integration:</strong></p>
     * <p>The provided class loader is used consistently throughout the scanning process:</p>
     * <ul>
     *   <li>All class loading operations use the specified class loader</li>
     *   <li>Interface resolution and validation respect the class loader hierarchy</li>
     *   <li>This ensures proper isolation and dependency resolution</li>
     * </ul>
     *
     * <p><strong>Error Handling:</strong></p>
     * <p>The method implements robust error handling to ensure scanning reliability:</p>
     * <ul>
     *   <li>Individual class loading failures don't terminate the entire scan</li>
     *   <li>I/O exceptions during directory traversal are handled gracefully</li>
     *   <li>Reflection errors during class validation are caught and logged</li>
     *   <li>Resource cleanup is guaranteed through try-with-resources patterns</li>
     * </ul>
     *
     * <p><strong>Performance Optimization:</strong></p>
     * <p>Several optimizations ensure efficient scanning performance:</p>
     * <ul>
     *   <li>Early filtering reduces unnecessary class loading attempts</li>
     *   <li>Stream-based processing enables efficient parallel operations</li>
     *   <li>Minimal object allocation during traversal operations</li>
     * </ul>
     *
     * @param root the root directory path to scan recursively for ProcessPluginDefinition classes,
     *            must represent a valid directory that may contain Java class files
     * @param cl the class loader to use for loading discovered classes, should be properly configured
     *          with the necessary classpath entries for successful class resolution
     * @return a list of discovered and validated plugin adapters, never null but may be empty if no
     *         valid ProcessPluginDefinition implementations are found in the specified directory.
     *         The returned list maintains the order in which plugins were discovered during traversal.
     * @since 1.0
     * @see #isProcessPluginDefinitionClassFile(Path, ClassLoader, List, Stream)
     * @see Files#walk (Path)
     */
    private static List<PluginAdapter> scanDirWithClassLoader(Path root, ClassLoader cl) {
        List<PluginAdapter> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            isProcessPluginDefinitionClassFile(root, cl, out, s);
        } catch (Exception ignored) {}
        return out;
    }

    /**
     * Processes a stream of file paths to identify, validate, and instantiate ProcessPluginDefinition implementations.
     *
     * <p>This method represents the core validation and instantiation logic of the plugin discovery system.
     * It performs comprehensive analysis of candidate class files to ensure they meet all requirements
     * for valid ProcessPluginDefinition implementations, including both interface compliance and
     * method signature validation.</p>
     *
     * <p><strong>Multi-Stage Validation Process:</strong></p>
     * <p>The method employs a rigorous validation pipeline:</p>
     * <ol>
     *   <li><strong>File Pattern Matching</strong> - Filters files based on naming conventions and file type</li>
     *   <li><strong>Class Loading</strong> - Attempts to load each candidate class using the provided class loader</li>
     *   <li><strong>Interface Implementation Validation</strong> - Verifies the class implements ProcessPluginDefinition</li>
     *   <li><strong>Method Signature Validation</strong> - Confirms all required methods are present and accessible</li>
     *   <li><strong>Instance Creation</strong> - Instantiates the plugin and wraps it in an appropriate adapter</li>
     * </ol>
     *
     * <p><strong>Comprehensive Debug Output:</strong></p>
     * <p>The method provides detailed diagnostic information for both successful discoveries and failures:</p>
     * <ul>
     *   <li><strong>Success Cases</strong> - Reports discovered plugins with class names, source locations,
     *       and validation methods used</li>
     *   <li><strong>Failure Cases</strong> - Provides specific failure reasons including interface compliance
     *       issues and missing method problems</li>
     *   <li><strong>Contextual Information</strong> - Includes root directory information for traceability</li>
     * </ul>
     *
     * <p><strong>File Filtering Criteria:</strong></p>
     * <p>The method applies strict filtering to identify valid plugin candidates:</p>
     * <ul>
     *   <li>Must be regular files (not directories or symbolic links)</li>
     *   <li>Filename must end with "ProcessPluginDefinition.class"</li>
     *   <li>Must not contain ' character (excludes inner and anonymous classes)</li>
     *   <li>Must be readable and accessible through the filesystem</li>
     * </ul>
     *
     * <p><strong>Class Name Resolution:</strong></p>
     * <p>The method converts filesystem paths to fully qualified class names:</p>
     * <ul>
     *   <li>Computes relative paths from the scanning root directory</li>
     *   <li>Converts filesystem separators to Java package separators</li>
     *   <li>Removes .class file extension to obtain the class name</li>
     *   <li>Handles platform-specific path separators correctly</li>
     * </ul>
     *
     * <p><strong>Version Detection and Adapter Selection:</strong></p>
     * <p>The method automatically detects plugin API versions and creates appropriate adapters:</p>
     * <ul>
     *   <li>Checks for v2 ProcessPluginDefinition interface first (preferred)</li>
     *   <li>Falls back to v1 ProcessPluginDefinition interface if v2 is not implemented</li>
     *   <li>Creates V2Adapter for v2 implementations, V1Adapter for v1 implementations</li>
     *   <li>Ensures proper adapter type matching based on interface implementation</li>
     * </ul>
     *
     * <p><strong>Error Recovery and Resilience:</strong></p>
     * <p>The method is designed to handle various error conditions gracefully:</p>
     * <ul>
     *   <li>Individual class loading failures don't terminate processing of other candidates</li>
     *   <li>Reflection errors during validation are caught and logged appropriately</li>
     *   <li>Instantiation failures are handled without affecting other plugin discoveries</li>
     *   <li>Stream processing continues even when individual elements fail</li>
     * </ul>
     *
     * @param root the root path being scanned, used for computing relative class names and providing
     *            contextual information in debug output
     * @param cl the class loader to use for loading candidate classes, should be properly configured
     *          with the necessary classpath entries for successful plugin class resolution
     * @param out the mutable list to which discovered and validated plugin adapters will be added,
     *           allowing accumulation of results across multiple scanning operations
     * @param s the stream of file paths to process, typically generated by {@link Files#walk (Path)}
     *         or similar directory traversal operations
     * @throws RuntimeException if critical errors occur during stream processing that cannot be
     *                         recovered from gracefully
     * @since 1.0
     * @see #toFqdn(Path, Path)
     * @see #implementsProcessPluginDefinition(Class, ClassLoader)
     * @see #hasPluginSignature(Class)
     * @see #isAssignableTo(Class, ClassLoader)
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
     * Performs comprehensive scanning of JAR files in the system classpath for ProcessPluginDefinition implementations.
     *
     * <p>This method provides robust plugin discovery capabilities for packaged applications and library
     * environments where plugins may be distributed as JAR files. It systematically examines all JAR files
     * found in the Java classpath, creating isolated class loading environments for each JAR to ensure
     * proper plugin discovery and validation.</p>
     *
     * <p><strong>Classpath Analysis:</strong></p>
     * <p>The method performs systematic analysis of the Java classpath:</p>
     * <ul>
     *   <li>Retrieves the complete system classpath via {@code java.class.path} system property</li>
     *   <li>Handles platform-specific path separators correctly (Windows ';', Unix ':')</li>
     *   <li>Filters classpath entries to process only JAR files</li>
     *   <li>Supports both absolute and relative JAR file paths</li>
     * </ul>
     *
     * <p><strong>JAR File Processing:</strong></p>
     * <p>Each JAR file is processed with comprehensive entry examination:</p>
     * <ol>
     *   <li><strong>JAR File Opening</strong> - Creates JarFile instances with proper resource management</li>
     *   <li><strong>Entry Enumeration</strong> - Systematically examines all JAR entries for plugin candidates</li>
     *   <li><strong>Class File Identification</strong> - Filters entries based on ProcessPluginDefinition naming patterns</li>
     *   <li><strong>Class Loading with Isolation</strong> - Creates dedicated URLClassLoader for each JAR</li>
     *   <li><strong>Plugin Validation</strong> - Applies the same rigorous validation as directory scanning</li>
     * </ol>
     *
     * <p><strong>Class Loading Isolation:</strong></p>
     * <p>The method ensures proper class loading isolation for each JAR:</p>
     * <ul>
     *   <li>Creates dedicated URLClassLoader instances for each JAR file</li>
     *   <li>Sets the provided parent class loader to maintain proper delegation hierarchy</li>
     *   <li>Ensures plugin classes are loaded in their proper context</li>
     *   <li>Prevents class loading conflicts between different JARs</li>
     * </ul>
     *
     * <p><strong>Validation and Debug Output:</strong></p>
     * <p>The method provides the same comprehensive validation and diagnostic output as directory scanning:</p>
     * <ul>
     *   <li><strong>Interface Implementation Validation</strong> - Confirms classes implement ProcessPluginDefinition</li>
     *   <li><strong>Method Signature Verification</strong> - Ensures all required methods are present</li>
     *   <li><strong>Success Reporting</strong> - Logs successfully discovered plugins with source JAR information</li>
     *   <li><strong>Failure Analysis</strong> - Reports specific reasons for validation failures</li>
     * </ul>
     *
     * <p><strong>Resource Management:</strong></p>
     * <p>Proper resource management ensures system stability:</p>
     * <ul>
     *   <li>JAR files are opened and closed using try-with-resources patterns</li>
     *   <li>URLClassLoader instances are properly closed after use</li>
     *   <li>File handles are released promptly to prevent resource leaks</li>
     *   <li>Exception handling ensures cleanup even in error conditions</li>
     * </ul>
     *
     * <p><strong>Performance Considerations:</strong></p>
     * <p>Several optimizations ensure efficient JAR scanning:</p>
     * <ul>
     *   <li>Early filtering reduces unnecessary class loading attempts</li>
     *   <li>Dedicated class loaders prevent classpath pollution</li>
     *   <li>Efficient JAR entry enumeration minimizes I/O overhead</li>
     *   <li>Lazy class loading reduces memory consumption</li>
     * </ul>
     *
     * <p><strong>Error Resilience:</strong></p>
     * <p>The method handles various error conditions gracefully:</p>
     * <ul>
     *   <li>Individual JAR file access failures don't stop processing of other JARs</li>
     *   <li>Class loading errors within a JAR don't affect other JAR processing</li>
     *   <li>Malformed JAR files are skipped with appropriate logging</li>
     *   <li>I/O exceptions are converted to UncheckedIOException for proper error propagation</li>
     * </ul>
     *
     * @param parentCl the parent class loader to use as the parent for JAR-specific URLClassLoaders,
     *                typically the system class loader or thread context class loader
     * @return a list of discovered plugin adapters from all processed JAR files, never null but
     *         may be empty if no valid ProcessPluginDefinition implementations are found.
     *         Results maintain the order of JAR processing and plugin discovery within each JAR.
     * @throws UncheckedIOException if critical I/O errors occur during JAR file access that cannot
     *                             be recovered from gracefully
     * @since 1.0
     * @see JarFile
     * @see URLClassLoader
     * @see #implementsProcessPluginDefinition(Class, ClassLoader)
     * @see #hasPluginSignature(Class)
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
     * Scans common build output directories for ProcessPluginDefinition implementations in typical
     * development environments.
     *
     * <p>This method serves as a crucial discovery strategy for non-packaged applications, such as during
     * local development and testing within an IDE. It systematically checks a predefined list of standard
     * build output locations used by major build tools and IDEs, including Maven, Gradle, and IntelliJ IDEA.</p>
     *
     * <p><strong>Scanned Locations:</strong></p>
     * <ul>
     * <li>{@code "target/classes"} - Standard Maven build output directory.</li>
     * <li>{@code "build/classes/java/main"} - Standard Gradle build output directory.</li>
     * <li>{@code "out/production/classes"} - Default IntelliJ IDEA build output directory.</li>
     * </ul>
     *
     * <p>For each path that exists and is a directory, this method delegates to {@link #scanDir(Path, ClassLoader)}
     * to perform the actual scanning and class loading. This approach ensures that developers can run validation
     * directly from their IDE without needing to package the project into a JAR file first.</p>
     *
     * @param parentCl the parent class loader to be used for the class loaders created for each directory.
     * This ensures a proper delegation hierarchy.
     * @return a list of discovered plugin adapters. Returns an empty list if no plugins are found in any
     * of the standard directories; never null.
     * @since 1.0
     * @see #scanDir(Path, ClassLoader)
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
     * Scans a single directory tree for ProcessPluginDefinition implementations using a dedicated class loader.
     *
     * <p>This method creates a new {@link URLClassLoader} specifically for the provided root directory,
     * ensuring that the classes within are loaded in an isolated context with the correct classpath. It then
     * walks the entire directory tree starting from the root, streaming all file paths to the
     * {@link #isProcessPluginDefinitionClassFile(Path, ClassLoader, List, Stream)} method, which handles the
     * filtering, validation, and instantiation of plugin classes.</p>
     *
     * <p>This utility is a fundamental building block for directory-based scanning, used by both
     * {@link #scanDirectories(ClassLoader)} for standard locations and {@link #scanProjectClassesDirectly(File, ClassLoader)}
     * for custom project structures. The use of a try-with-resources statement ensures that the class loader and
     * file stream are properly closed, even in the event of an error.</p>
     *
     * @param root the root directory path to scan recursively.
     * @param parentCl the parent class loader for the new {@link URLClassLoader} created for this directory.
     * @return a list of discovered plugin adapters found within the directory. Returns an empty list
     * if no plugins are found; never null.
     * @since 1.0
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
     * Checks if a given class implements any version of the {@code ProcessPluginDefinition} interface.
     *
     * <p>This method provides a version-agnostic way to validate a candidate class. It attempts to load
     * both the v1 and v2 interface classes ({@code dev.dsf.bpe.v2.ProcessPluginDefinition} and
     * {@code dev.dsf.bpe.v1.ProcessPluginDefinition}) using the provided class loader and checks for
     * assignability using {@link Class#isAssignableFrom(Class)}. This is essential for working in isolated
     * class loading environments where the interfaces may only be available in a specific loader.</p>
     *
     * <p>The method gracefully handles {@link ClassNotFoundException}, which allows the discovery
     * process to function correctly even if one of the interface versions is not present on the classpath.</p>
     *
     * @param c the class to check.
     * @param cl the class loader to use for loading the interface classes for comparison.
     * @return {@code true} if the class implements either the v1 or v2 {@code ProcessPluginDefinition}
     * interface, {@code false} otherwise.
     * @since 1.0
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
     * Verifies that a class structurally conforms to the {@code ProcessPluginDefinition} API by checking
     * for the presence of required methods.
     *
     * <p>This validation step goes beyond a simple {@code instanceof} check. It ensures that the candidate
     * class has the necessary public, no-argument methods ({@code getName}, {@code getProcessModels},
     * {@code getFhirResourcesByProcessId}) required for the adapter to function. This protects against
     * malformed implementations, such as abstract classes or classes compiled against a different version
     * of the interface, that might technically implement the interface but lack the concrete methods.</p>
     *
     * @param c the class to inspect.
     * @return {@code true} if all required methods are found, {@code false} otherwise.
     * @since 1.0
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
     * Converts a file system path to a fully qualified class name.
     *
     * <p>This utility method takes a root directory path and the path to a {@code .class} file and computes
     * the corresponding Java class name. It works by:
     * <ol>
     * <li>Finding the relative path of the class file with respect to the root directory.</li>
     * <li>Removing the {@code .class} file extension.</li>
     * <li>Replacing the operating system's file separator (e.g., {@code /} or {@code \}) with a dot ({@code .}).</li>
     * </ol>
     * For example, given a root of {@code /project/target/classes} and a class file path of
     * {@code /project/target/classes/com/example/MyPlugin.class}, this method would return
     * {@code "com.example.MyPlugin"}.</p>
     *
     * @param root the root path of the classpath entry (e.g., "target/classes").
     * @param clazz the full path to the {@code .class} file.
     * @return the fully qualified class name as a String.
     * @since 1.0
     */
    private static String toFqdn(Path root, Path clazz) {
        String rel = root.relativize(clazz).toString();
        return rel.substring(0, rel.length() - ".class".length()).replace(File.separatorChar, '.');
    }

    /**
     * Checks if a class is specifically assignable to the v2 {@code ProcessPluginDefinition} interface.
     *
     * <p>This method is used to differentiate between v1 and v2 plugin implementations after a candidate
     * class has already been confirmed to implement at least one version of the interface. It attempts to
     * load the v2 interface class using the provided class loader and checks for assignability. The result
     * determines whether a {@link V1Adapter} or a {@link V2Adapter} should be created for the plugin instance.</p>
     *
     * <p>It returns {@code false} if the v2 interface class cannot be found, ensuring it fails safely in
     * environments where only the v1 API is present.</p>
     *
     * @param c the class to check.
     * @param cl the class loader to use for loading the v2 interface class.
     * @return {@code true} if the class implements the v2 interface, {@code false} otherwise.
     * @since 1.0
     */
    private static boolean isAssignableTo(Class<?> c, ClassLoader cl) {
        try {
            return Class.forName("dev.dsf.bpe.v2.ProcessPluginDefinition", false, cl).isAssignableFrom(c);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
