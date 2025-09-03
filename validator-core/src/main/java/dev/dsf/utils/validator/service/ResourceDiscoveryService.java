package dev.dsf.utils.validator.service;

import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.plugin.PluginDefinitionDiscovery;
import dev.dsf.utils.validator.plugin.PluginDefinitionDiscovery.PluginAdapter;
import dev.dsf.utils.validator.util.resource.ResourceResolver;
import dev.dsf.utils.validator.util.api.ApiVersionDetector;
import dev.dsf.utils.validator.util.api.ApiVersionHolder;
import dev.dsf.utils.validator.util.resource.FhirAuthorizationCache;
import dev.dsf.utils.validator.setup.ProjectSetupHandler.ProjectContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for discovering and collecting resources from plugin definitions.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Plugin definition discovery and validation</li>
 *   <li>Resource root determination</li>
 *   <li>BPMN and FHIR resource collection</li>
 *   <li>API version detection</li>
 *   <li>Authorization cache initialization</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class ResourceDiscoveryService {

    private final Logger logger;
    private final ApiVersionDetector apiVersionDetector;

    /**
     * Constructs a new ResourceDiscoveryService.
     *
     * @param logger the logger for output messages
     */
    public ResourceDiscoveryService(Logger logger) {
        this.logger = logger;
        this.apiVersionDetector = new ApiVersionDetector();
    }

    /**
     * Discovers all resources from the project context.
     *
     * @param context the project context containing setup information
     * @return a DiscoveryResult containing all discovered resources
     * @throws IllegalStateException if plugin discovery fails
     */
    public DiscoveryResult discoverResources(ProjectContext context) throws IllegalStateException, MissingServiceRegistrationException {
        // 1. Discover plugin definition
        PluginAdapter pluginAdapter = discoverPluginDefinition(context.projectDir());

        // 2. Determine resources root
        File resourcesDir = determineResourcesRoot(
                context.projectDir(),
                pluginAdapter,
                context.resourcesDir()
        );

        logger.info("Resources root in use: " + resourcesDir.getAbsolutePath());

        // 3. Detect API version
        detectAndSetApiVersion(context.projectPath());

        // 4. Initialize authorization cache
        initializeAuthorizationCache(context.projectDir());

        // 5. Collect referenced resources
        Set<String> referencedBpmnPaths = collectReferencedBpmnPaths(pluginAdapter);
        Set<String> referencedFhirPaths = collectReferencedFhirPaths(pluginAdapter);

        // 6. Resolve files
        ResolvedResources bpmnResources = resolveResourceFiles(referencedBpmnPaths, resourcesDir, "BPMN");
        ResolvedResources fhirResources = resolveResourceFiles(referencedFhirPaths, resourcesDir, "FHIR");

        logDiscoveryResults(
                referencedBpmnPaths, bpmnResources,
                referencedFhirPaths, fhirResources
        );

        Set<String> allReferencedPaths = new HashSet<>();
        allReferencedPaths.addAll(referencedBpmnPaths);
        allReferencedPaths.addAll(referencedFhirPaths);

        return new DiscoveryResult(
                pluginAdapter,
                resourcesDir,
                bpmnResources.resolvedFiles,
                fhirResources.resolvedFiles,
                bpmnResources.missingRefs,
                fhirResources.missingRefs,
                allReferencedPaths
        );
    }

    /**
     * Discovers the single ProcessPluginDefinition from the project.
     *
     * @param projectDir the project directory
     * @return the discovered PluginAdapter
     * @throws IllegalStateException if discovery fails
     */
    private PluginAdapter discoverPluginDefinition(File projectDir) throws IllegalStateException {
        PluginAdapter pluginAdapter = PluginDefinitionDiscovery.discoverSingle(projectDir);
        logger.info("Discovered ProcessPluginDefinition: " + pluginAdapter.sourceClass().getName());

        // Log code source for diagnostics
        try {
            var cs = pluginAdapter.sourceClass().getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                logger.debug("Plugin class CodeSource: " + cs.getLocation());
            } else {
                logger.debug("Plugin class CodeSource: <null> (class may come from a special loader/JAR)");
            }
        } catch (Exception ignore) {
            // Debug only
        }

        return pluginAdapter;
    }

    /**
     * Determines the proper resources root directory.
     *
     * @param projectDir the project directory
     * @param pluginAdapter the plugin adapter
     * @param fallback the fallback directory
     * @return the determined resources root
     */
    public File determineResourcesRoot(File projectDir, PluginAdapter pluginAdapter, File fallback) {
        try {
            Class<?> pluginClass = pluginAdapter.sourceClass();
            if (pluginClass != null) {
                java.security.ProtectionDomain pd = pluginClass.getProtectionDomain();
                if (pd != null) {
                    java.security.CodeSource cs = pd.getCodeSource();
                    if (cs != null && cs.getLocation() != null) {
                        java.net.URI uri = cs.getLocation().toURI();
                        Path loc = Paths.get(uri);

                        if (Files.isDirectory(loc)) {
                            String norm = loc.toString().replace('\\', '/');

                            // Maven: <module>/target/classes → use directly
                            if (norm.endsWith("/target/classes")) {
                                return loc.toFile();
                            }

                            // Gradle: prefer <module>/build/resources/main if classes root is returned
                            if (norm.endsWith("/build/classes/java/main")) {
                                Path gradleRes = loc.getParent() // java
                                        .getParent() // classes
                                        .resolve("resources")
                                        .resolve("main");
                                if (Files.isDirectory(gradleRes)) {
                                    return gradleRes.toFile();
                                }
                                // Fall back to classes dir if resources dir is missing
                                return loc.toFile();
                            }

                            // Unknown layout but still a directory on the classpath – use it
                            return loc.toFile();
                        }

                        // Code source points to a JAR or non-directory → use fallback
                        return (fallback != null) ? fallback : projectDir;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error determining resources root from code source: " + e.getMessage());
        }

        // Local source checkout fallbacks (Maven/Gradle)
        File mavenResources = new File(projectDir, "src/main/resources");
        if (mavenResources.isDirectory()) return mavenResources;

        File mavenClasses = new File(projectDir, "target/classes");
        if (mavenClasses.isDirectory()) return mavenClasses;

        File gradleResources = new File(projectDir, "build/resources/main");
        if (gradleResources.isDirectory()) return gradleResources;

        return (fallback != null) ? fallback : projectDir;
    }

    /**
     * Detects and sets the DSF BPE API version.
     *
     * @param projectPath the project path
     */
    private void detectAndSetApiVersion(Path projectPath) throws MissingServiceRegistrationException {
        apiVersionDetector.detect(projectPath).ifPresent(v -> {
            ApiVersionHolder.setVersion(v.version());
            logger.info("Detected DSF BPE API version: " + v.version());
        });
    }

    /**
     * Initializes the FHIR authorization cache.
     *
     * @param projectDir the project directory
     */
    private void initializeAuthorizationCache(File projectDir) {
        FhirAuthorizationCache.seedFromProjectAndClasspath(projectDir);
        logger.debug("FHIR authorization cache initialized.");
    }

    /**
     * Collects all referenced BPMN paths from the plugin adapter.
     *
     * @param pluginAdapter the plugin adapter
     * @return set of BPMN resource paths
     */
    private Set<String> collectReferencedBpmnPaths(PluginAdapter pluginAdapter) {
        logger.info("Gathering referenced BPMN resources from " +
                pluginAdapter.sourceClass().getSimpleName() + "...");

        return pluginAdapter.getProcessModels().stream()
                .map(ResourceResolver::normalizeRef)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Collects all referenced FHIR paths from the plugin adapter.
     *
     * @param pluginAdapter the plugin adapter
     * @return set of FHIR resource paths
     */
    private Set<String> collectReferencedFhirPaths(PluginAdapter pluginAdapter) {
        logger.info("Gathering referenced FHIR resources from " +
                pluginAdapter.sourceClass().getSimpleName() + "...");

        return pluginAdapter.getFhirResourcesByProcessId().values().stream()
                .flatMap(Collection::stream)
                .map(ResourceResolver::normalizeRef)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Resolves resource files from their reference paths.
     *
     * @param referencedPaths the set of referenced resource paths
     * @param resourcesDir the resources directory
     * @param resourceType the type of resource (for logging)
     * @return resolved resources with files and missing references
     */
    private ResolvedResources resolveResourceFiles(
            Set<String> referencedPaths, File resourcesDir, String resourceType) {

        List<File> resolvedFiles = referencedPaths.stream()
                .map(this::cleanRef)
                .map(ref -> ResourceResolver.resolveToFile(ref, resourcesDir))
                .flatMap(Optional::stream)
                .distinct()
                .collect(Collectors.toList());

        List<String> missingRefs = referencedPaths.stream()
                .map(this::cleanRef)
                .filter(ref -> ResourceResolver.resolveToFile(ref, resourcesDir).isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (!referencedPaths.isEmpty() && resolvedFiles.isEmpty()) {
            logger.warn("Sanity check: No " + resourceType + " files resolved although " +
                    resourceType + " references exist. " +
                    "If the plugin was loaded from a JAR, ensure ResourceResolver can read via ClassLoader.");
        }

        return new ResolvedResources(resolvedFiles, missingRefs);
    }

    /**
     * Normalizes a resource reference for filesystem/classpath resolution.
     *
     * @param ref the reference to clean
     * @return cleaned reference
     */
    private String cleanRef(String ref) {
        if (ref == null) return "";
        String r = ref.trim();

        // Drop classpath: prefix
        if (r.startsWith("classpath:")) {
            r = r.substring("classpath:".length());
        }

        // Unify separators
        r = r.replace('\\', '/');

        // Remove all leading slashes
        while (r.startsWith("/")) {
            r = r.substring(1);
        }

        return r;
    }

    /**
     * Logs the discovery results for debugging.
     */
    private void logDiscoveryResults(
            Set<String> referencedBpmnPaths, ResolvedResources bpmnResources,
            Set<String> referencedFhirPaths, ResolvedResources fhirResources) {

        logger.info("Referenced BPMN: " + referencedBpmnPaths.size()
                + " -> resolved: " + bpmnResources.resolvedFiles.size()
                + ", missing: " + bpmnResources.missingRefs.size());

        logger.info("Referenced FHIR: " + referencedFhirPaths.size()
                + " -> resolved: " + fhirResources.resolvedFiles.size()
                + ", missing: " + fhirResources.missingRefs.size());

        if (!bpmnResources.missingRefs.isEmpty()) {
            logger.warn("Sample missing BPMN ref: " + bpmnResources.missingRefs.getFirst());
        }
        if (!fhirResources.missingRefs.isEmpty()) {
            logger.warn("Sample missing FHIR ref: " + fhirResources.missingRefs.getFirst());
        }
    }

    /**
         * Internal class for holding resolved resources and missing references.
         */
        private record ResolvedResources(List<File> resolvedFiles, List<String> missingRefs) {
    }

    /**
         * Data class containing the complete discovery result.
         */
        public record DiscoveryResult(PluginAdapter pluginAdapter, File resourcesDir, List<File> bpmnFiles,
                                      List<File> fhirFiles, List<String> missingBpmnRefs, List<String> missingFhirRefs,
                                      Set<String> referencedPaths) {
    }
}