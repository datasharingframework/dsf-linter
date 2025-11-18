package dev.dsf.linter.service;

import dev.dsf.linter.exception.MissingServiceRegistrationException;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.plugin.EnhancedPluginDefinitionDiscovery;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.PluginVersionUtils;
import dev.dsf.linter.util.loader.ClassLoaderUtils;
import dev.dsf.linter.util.resource.FhirAuthorizationCache;
import dev.dsf.linter.util.resource.ResourceDiscoveryUtils;
import dev.dsf.linter.util.resource.ResourceResolutionResult;
import dev.dsf.linter.util.resource.ResourceResolutionService;
import dev.dsf.linter.util.resource.ResourceRootResolver;
import dev.dsf.linter.setup.ProjectSetupHandler.ProjectContext;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Unified resource discovery service with enhanced resource root linter
 * and dependency JAR scanning.
 * Tracks plugin-specific resource roots and lints resource locations.
 */
public class ResourceDiscoveryService {

    private final Logger logger;
    private final ResourceResolutionService resolutionService;

    /**
     * Enhanced plugin discovery result with resource root linter and dependency tracking.
     */
    public record PluginDiscovery(
            PluginDefinitionDiscovery.PluginAdapter adapter,
            ApiVersion apiVersion,
            File resourcesDir,
            File pluginSpecificResourceRoot,
            List<File> bpmnFiles,
            List<File> fhirFiles,
            List<String> missingBpmnRefs,
            List<String> missingFhirRefs,
            Map<String, ResourceResolutionResult> bpmnOutsideRoot,
            Map<String, ResourceResolutionResult> fhirOutsideRoot,
            Map<String, ResourceResolutionResult> bpmnFromDependencies,
            Map<String, ResourceResolutionResult> fhirFromDependencies,
            Set<String> referencedPaths
    ) {}

    /**
     * Discovery result that always contains a Map of plugins.
     */
    public record DiscoveryResult(
            Map<String, PluginDiscovery> plugins,
            File sharedResourcesDir,
            Set<ApiVersion> detectedVersions,
            boolean hasFailedPlugins
    ) {
        public DiscoveryStatistics getStatistics() {
            int totalBpmnFiles = 0;
            int totalFhirFiles = 0;
            int totalMissingBpmn = 0;
            int totalMissingFhir = 0;
            int totalOutsideRoot = 0;
            int totalFromDependencies = 0;

            for (PluginDiscovery plugin : plugins.values()) {
                totalBpmnFiles += plugin.bpmnFiles().size();
                totalFhirFiles += plugin.fhirFiles().size();
                totalMissingBpmn += plugin.missingBpmnRefs().size();
                totalMissingFhir += plugin.missingFhirRefs().size();
                totalOutsideRoot += plugin.bpmnOutsideRoot().size() + plugin.fhirOutsideRoot().size();
                totalFromDependencies += plugin.bpmnFromDependencies().size() + plugin.fhirFromDependencies().size();
            }

            return new DiscoveryStatistics(
                    totalBpmnFiles, totalFhirFiles,
                    totalMissingBpmn, totalMissingFhir,
                    totalOutsideRoot, totalFromDependencies
            );
        }
    }

    /**
     * Enhanced statistics with outside-root and dependency tracking.
     */
    public record DiscoveryStatistics(
            int bpmnFiles,
            int fhirFiles,
            int missingBpmn,
            int missingFhir,
            int outsideRoot,
            int fromDependencies
    ) {}

    public ResourceDiscoveryService(Logger logger) {
        this.logger = logger;
        this.resolutionService = new ResourceResolutionService();
    }

    /**
     * Main discovery method with enhanced resource root linter and dependency scanning.
     */
    public DiscoveryResult discover(ProjectContext context)
            throws IllegalStateException, MissingServiceRegistrationException {

        logger.info("Starting unified plugin resource discovery with root linting and dependency scanning...");

        EnhancedPluginDefinitionDiscovery.DiscoveryResult pluginDiscovery;

        try {
            pluginDiscovery = ClassLoaderUtils.withTemporaryContextClassLoader(
                    context.projectClassLoader(),
                    () -> EnhancedPluginDefinitionDiscovery.discoverAll(context.projectDir())
            );
        } catch (Exception e) {
            throw new IllegalStateException("Plugin discovery failed: " + e.getMessage(), e);
        }

        // Report plugin discovery results (including failures)
        if (!pluginDiscovery.getFailedPlugins().isEmpty()) {
            logger.error("");
            logger.error("═══════════════════════════════════════════════════════════════");
            logger.error("PLUGIN DISCOVERY ERRORS - Some plugins failed to load");
            logger.error("═══════════════════════════════════════════════════════════════");
            
            for (dev.dsf.linter.plugin.PluginDiscoveryError error : pluginDiscovery.getFailedPlugins()) {
                logger.error("");
                logger.error("✗ Plugin: " + error.pluginClassName());
                logger.error("  Error Type: " + error.errorType());
                logger.error("  Message: " + error.errorMessage());
                logger.error("  Location: " + error.location());
            }
            
            logger.error("");
            logger.error("Summary:");
            logger.error("  ✓ Successfully discovered: " + pluginDiscovery.getAllPlugins().size() + " plugin(s)");
            logger.error("  ✗ Failed to discover: " + pluginDiscovery.getFailedPlugins().size() + " plugin(s)");
            logger.error("═══════════════════════════════════════════════════════════════");
            logger.error("");
            logger.warn("Continuing with " + pluginDiscovery.getAllPlugins().size() + " valid plugin(s)...");
            logger.warn("Note: Failed plugins will not be linted. Exit code will be non-zero.");
        }
        
        if (pluginDiscovery.getAllPlugins().isEmpty()) {
            throw new IllegalStateException("""
                    No ProcessPluginDefinition implementations found
                    Possible causes:
                      - The project is not set up correctly as a DSF Process Plugin project
                      - The JAR file does not contain META-INF/services/dev.dsf.bpe.v1.ProcessPluginDefinition
                      - Build the Maven project first: mvn clean package
                      - Then lint the resulting JAR file from target/ directory
                      If it still doesn't work, please report the issue on GitHub.""");
        }

        logger.info("Found " + pluginDiscovery.getAllPlugins().size() + " plugin(s)");

        // Use centralized resource root resolution with first plugin
        ResourceRootResolver.ResolutionResult resourceRootResult =
                ResourceRootResolver.resolveResourceRoot(
                        context.projectDir(),
                        pluginDiscovery.getAllPlugins().get(0)
                );

        File sharedResourcesDir = resourceRootResult.resourceRoot();
        logger.info("Resolved shared resources directory: " + resourceRootResult);

        // Initialize shared components
        FhirAuthorizationCache.setLogger(logger);
        FhirAuthorizationCache.seedFromProjectAndClasspath(context.projectDir());
        logger.debug("FHIR authorization cache initialized.");

        // Process each plugin into unified structure
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
                        adapter, sharedResourcesDir, context.projectPath(), context.projectDir()
                );

                plugins.put(uniqueName, discovery);
                detectedVersions.add(discovery.apiVersion());

                logPluginStatistics(uniqueName, discovery);
            }
        }

        return new DiscoveryResult(plugins, sharedResourcesDir, detectedVersions, pluginDiscovery.hasFailedPlugins());
    }

    /**
     * Discovers resources for a single plugin with strict root linting and dependency scanning.
     */
    private PluginDiscovery discoverSinglePlugin(
            PluginDefinitionDiscovery.PluginAdapter adapter,
            File sharedResourcesDir,
            Path projectPath,
            File projectDir)
            throws MissingServiceRegistrationException {

        ApiVersion apiVersion = detectPluginApiVersion(adapter);

        File pluginSpecificRoot = resolvePluginSpecificResourceRoot(adapter, sharedResourcesDir);

        logger.debug("Plugin-specific resource root: " + pluginSpecificRoot.getAbsolutePath());

        Set<String> referencedBpmnPaths = resolutionService.collectBpmnPaths(adapter);
        Set<String> referencedFhirPaths = resolutionService.collectFhirPaths(adapter);

        logger.debug("Referenced BPMN: " + referencedBpmnPaths.size() +
                ", Referenced FHIR: " + referencedFhirPaths.size());

        // Enhanced resolution with dependency scanning
        ResourceDiscoveryUtils.StrictResolvedResources bpmnResources =
                ResourceDiscoveryUtils.resolveResourceFilesStrict(
                        referencedBpmnPaths, pluginSpecificRoot, projectDir
                );
        ResourceDiscoveryUtils.StrictResolvedResources fhirResources =
                ResourceDiscoveryUtils.resolveResourceFilesStrict(
                        referencedFhirPaths, pluginSpecificRoot, projectDir
                );

        Set<String> allReferencedPaths = new HashSet<>();
        allReferencedPaths.addAll(referencedBpmnPaths);
        allReferencedPaths.addAll(referencedFhirPaths);

        return new PluginDiscovery(
                adapter,
                apiVersion,
                sharedResourcesDir,
                pluginSpecificRoot,
                bpmnResources.lintFiles(),
                fhirResources.lintFiles(),
                bpmnResources.missingRefs(),
                fhirResources.missingRefs(),
                bpmnResources.outsideRootFiles(),
                fhirResources.outsideRootFiles(),
                bpmnResources.dependencyFiles(),
                fhirResources.dependencyFiles(),
                allReferencedPaths
        );
    }

    /**
     * Resolves plugin-specific resource root directory.
     */
    private File resolvePluginSpecificResourceRoot(
            PluginDefinitionDiscovery.PluginAdapter adapter,
            File fallbackRoot) {

        ResourceRootResolver.ResolutionResult result =
                ResourceRootResolver.resolveResourceRootForPlugin(
                        fallbackRoot.getParentFile(),
                        adapter
                );

        return result.resourceRoot();
    }

    /**
     * Detects API version for a specific plugin based on adapter type.
     * After the validation improvements in plugin discovery, the adapter is guaranteed 
     * to be either V1Adapter or V2Adapter, so this method always returns a valid version.
     */
    private ApiVersion detectPluginApiVersion(PluginDefinitionDiscovery.PluginAdapter adapter) {

        ApiVersion version = PluginVersionUtils.detectApiVersion(adapter);

        // Sanity check: This should never be UNKNOWN after plugin discovery validation
        if (version == ApiVersion.UNKNOWN) {
            throw new IllegalStateException(
                "Internal error: Valid adapter exists but version is UNKNOWN. " +
                "This indicates a bug in plugin discovery. " +
                "Adapter type: " + adapter.getClass().getName()
            );
        }

        logger.debug("Plugin uses API " + version + " (determined by adapter type)");
        return version;
    }

    /**
     * Logs statistics for a single plugin including dependency-based resources.
     */
    private void logPluginStatistics(String pluginName, PluginDiscovery discovery) {
        logger.info(String.format(
                "Plugin '%s': BPMN files=%d (missing=%d, outside-root=%d, from-dependencies=%d), " +
                        "FHIR files=%d (missing=%d, outside-root=%d, from-dependencies=%d)",
                pluginName,
                discovery.bpmnFiles().size(),
                discovery.missingBpmnRefs().size(),
                discovery.bpmnOutsideRoot().size(),
                discovery.bpmnFromDependencies().size(),
                discovery.fhirFiles().size(),
                discovery.missingFhirRefs().size(),
                discovery.fhirOutsideRoot().size(),
                discovery.fhirFromDependencies().size()
        ));

        if (!discovery.bpmnOutsideRoot().isEmpty()) {
            logger.warn("Found " + discovery.bpmnOutsideRoot().size() +
                    " BPMN file(s) outside expected resource root");
        }

        if (!discovery.fhirOutsideRoot().isEmpty()) {
            logger.warn("Found " + discovery.fhirOutsideRoot().size() +
                    " FHIR file(s) outside expected resource root");
        }

        // Log dependency-based resources as INFO (not warning)
        if (!discovery.bpmnFromDependencies().isEmpty()) {
            logger.info("Found " + discovery.bpmnFromDependencies().size() +
                    " BPMN file(s) in dependency JARs (for showing those discovered files, you have to active verbose mode):");
            discovery.bpmnFromDependencies().forEach((ref, result) ->
                    logger.debug("  - " + ref + " (" + result.actualLocation() + ")")
            );
        }

        if (!discovery.fhirFromDependencies().isEmpty()) {
            logger.info("Found " + discovery.fhirFromDependencies().size() +
                    " FHIR file(s) in dependency JARs:");
            discovery.fhirFromDependencies().forEach((ref, result) ->
                    logger.debug("  - " + ref + " (" + result.actualLocation() + ")")
            );
        }
    }
}