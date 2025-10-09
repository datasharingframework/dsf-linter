package dev.dsf.utils.validator.service;

import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.plugin.EnhancedPluginDefinitionDiscovery;
import dev.dsf.utils.validator.plugin.PluginDefinitionDiscovery;
import dev.dsf.utils.validator.util.api.ApiVersion;
import dev.dsf.utils.validator.util.api.ApiVersionDetector;
import dev.dsf.utils.validator.util.api.DetectedVersion;
import dev.dsf.utils.validator.util.resource.FhirAuthorizationCache;
import dev.dsf.utils.validator.util.resource.ResourceDiscoveryUtils;
import dev.dsf.utils.validator.setup.ProjectSetupHandler.ProjectContext;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Unified resource discovery service that handles any number of plugins.
 * Always returns a Map of plugins for consistent processing.
 * A single plugin is simply a special case with one entry in the Map.
 */
public class ResourceDiscoveryService {

    private final Logger logger;
    private final ApiVersionDetector apiVersionDetector;

    /**
     * Single plugin discovery result containing all resource information
     */
    public record PluginDiscovery(
            PluginDefinitionDiscovery.PluginAdapter adapter,
            ApiVersion apiVersion,
            File resourcesDir,
            List<File> bpmnFiles,
            List<File> fhirFiles,
            List<String> missingBpmnRefs,
            List<String> missingFhirRefs,
            Set<String> referencedPaths
    ) {}

    /**
     * Discovery result that always contains a Map of plugins.
     * This Map may contain one or more entries.
     */
    public record DiscoveryResult(
            Map<String, PluginDiscovery> plugins,
            File sharedResourcesDir,
            Set<ApiVersion> detectedVersions
    ) {
        /**
         * Get combined statistics across all plugins
         */
        public DiscoveryStatistics getStatistics() {
            int totalBpmnFiles = 0;
            int totalFhirFiles = 0;
            int totalMissingBpmn = 0;
            int totalMissingFhir = 0;

            for (PluginDiscovery plugin : plugins.values()) {
                totalBpmnFiles += plugin.bpmnFiles().size();
                totalFhirFiles += plugin.fhirFiles().size();
                totalMissingBpmn += plugin.missingBpmnRefs().size();
                totalMissingFhir += plugin.missingFhirRefs().size();
            }

            return new DiscoveryStatistics(
                    totalBpmnFiles, totalFhirFiles,
                    totalMissingBpmn, totalMissingFhir
            );
        }
    }

    /**
     * Statistics holder for discovery results
     */
    public record DiscoveryStatistics(
            int bpmnFiles,
            int fhirFiles,
            int missingBpmn,
            int missingFhir
    ) {}

    public ResourceDiscoveryService(Logger logger) {
        this.logger = logger;
        this.apiVersionDetector = new ApiVersionDetector();
    }

    /**
     * Main discovery method that always returns a Map of plugins.
     * Works uniformly for any number of plugins (one or more).
     *
     * @param context the project context
     * @return discovery result containing all plugins as a Map
     */
    public DiscoveryResult discover(ProjectContext context)
            throws IllegalStateException, MissingServiceRegistrationException {

        logger.info("Starting unified plugin resource discovery...");

        // 1. Discover all plugins
        EnhancedPluginDefinitionDiscovery.DiscoveryResult pluginDiscovery =
                EnhancedPluginDefinitionDiscovery.discoverAll(context.projectDir());

        if (pluginDiscovery.getAllPlugins().isEmpty()) {
            throw new IllegalStateException("No ProcessPluginDefinition implementations found");
        }

        logger.info("Found " + pluginDiscovery.getAllPlugins().size() + " plugin(s)");

        // 2. Determine shared resources directory
        File sharedResourcesDir = ResourceDiscoveryUtils.determineResourcesRoot(
                context.projectDir(),
                pluginDiscovery.getAllPlugins().get(0),
                context.resourcesDir()
        );

        logger.info("Resources root: " + sharedResourcesDir.getAbsolutePath());

        // 3. Initialize shared components
        FhirAuthorizationCache.seedFromProjectAndClasspath(context.projectDir());
        logger.debug("FHIR authorization cache initialized.");

        // 4. Process each plugin into unified structure
        Map<String, PluginDiscovery> plugins = new LinkedHashMap<>();
        Set<ApiVersion> detectedVersions = new HashSet<>();

        for (Map.Entry<String, List<PluginDefinitionDiscovery.PluginAdapter>> entry :
                pluginDiscovery.getPluginsByName().entrySet()) {

            String pluginGroupName = entry.getKey();
            List<PluginDefinitionDiscovery.PluginAdapter> pluginList = entry.getValue();

            int counter = 0;
            for (PluginDefinitionDiscovery.PluginAdapter adapter : pluginList) {
                String uniqueName = ResourceDiscoveryUtils.generateUniquePluginName(
                        pluginGroupName, adapter, counter++, plugins.keySet()
                );

                logger.info("Processing plugin: " + uniqueName +
                        " (" + adapter.sourceClass().getName() + ")");

                PluginDiscovery discovery = discoverSinglePlugin(
                        adapter, sharedResourcesDir, context.projectPath()
                );

                plugins.put(uniqueName, discovery);
                detectedVersions.add(discovery.apiVersion());

                // Log statistics
                logger.info(String.format(
                        "Plugin '%s': BPMN files=%d (missing=%d), FHIR files=%d (missing=%d)",
                        uniqueName,
                        discovery.bpmnFiles().size(),
                        discovery.missingBpmnRefs().size(),
                        discovery.fhirFiles().size(),
                        discovery.missingFhirRefs().size()
                ));
            }
        }

        return new DiscoveryResult(plugins, sharedResourcesDir, detectedVersions);
    }

    /**
     * Discovers resources for a single plugin.
     */
    private PluginDiscovery discoverSinglePlugin(
            PluginDefinitionDiscovery.PluginAdapter adapter, File resourcesDir, Path projectPath)
            throws MissingServiceRegistrationException {

        // Detect API version
        ApiVersion apiVersion = detectPluginApiVersion(adapter, projectPath);

        // Collect references
        Set<String> referencedBpmnPaths = ResourceDiscoveryUtils.collectBpmnPaths(adapter);
        Set<String> referencedFhirPaths = ResourceDiscoveryUtils.collectFhirPaths(adapter);

        logger.debug("Referenced BPMN: " + referencedBpmnPaths.size() +
                ", Referenced FHIR: " + referencedFhirPaths.size());

        // Resolve files
        ResourceDiscoveryUtils.ResolvedResources bpmnResources =
                ResourceDiscoveryUtils.resolveResourceFiles(referencedBpmnPaths, resourcesDir);
        ResourceDiscoveryUtils.ResolvedResources fhirResources =
                ResourceDiscoveryUtils.resolveResourceFiles(referencedFhirPaths, resourcesDir);

        // Combine all referenced paths
        Set<String> allReferencedPaths = new HashSet<>();
        allReferencedPaths.addAll(referencedBpmnPaths);
        allReferencedPaths.addAll(referencedFhirPaths);

        return new PluginDiscovery(
                adapter,
                apiVersion,
                resourcesDir,
                bpmnResources.resolvedFiles(),
                fhirResources.resolvedFiles(),
                bpmnResources.missingRefs(),
                fhirResources.missingRefs(),
                allReferencedPaths
        );
    }

    /**
     * Detects API version for a specific plugin.
     */
    private ApiVersion detectPluginApiVersion(PluginDefinitionDiscovery.PluginAdapter adapter, Path projectPath)
            throws MissingServiceRegistrationException {

        // Check adapter type directly
        if (adapter instanceof PluginDefinitionDiscovery.V2Adapter) {
            logger.debug("Plugin uses API v2 (determined by adapter type)");
            return ApiVersion.V2;
        } else if (adapter instanceof PluginDefinitionDiscovery.V1Adapter) {
            logger.debug("Plugin uses API v1 (determined by adapter type)");
            return ApiVersion.V1;
        }

        // Fallback to detector
        return apiVersionDetector.detect(projectPath)
                .map(DetectedVersion::version)
                .orElseGet(() -> {
                    logger.warn("Could not detect API version. Using UNKNOWN.");
                    return ApiVersion.UNKNOWN;
                });
    }
}