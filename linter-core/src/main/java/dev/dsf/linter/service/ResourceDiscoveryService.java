package dev.dsf.linter.service;

import dev.dsf.linter.exception.MissingServiceRegistrationException;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.plugin.EnhancedPluginDefinitionDiscovery;
import dev.dsf.linter.plugin.GenericPluginAdapter;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionDetector;
import dev.dsf.linter.util.api.DetectedVersion;
import dev.dsf.linter.util.loader.ClassLoaderUtils;
import dev.dsf.linter.util.resource.FhirAuthorizationCache;
import dev.dsf.linter.util.resource.ResourceDiscoveryUtils;
import dev.dsf.linter.util.resource.ResourceResolver;
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
    private final ApiVersionDetector apiVersionDetector;

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
            Map<String, ResourceResolver.ResolutionResult> bpmnOutsideRoot,
            Map<String, ResourceResolver.ResolutionResult> fhirOutsideRoot,
            Map<String, ResourceResolver.ResolutionResult> bpmnFromDependencies,
            Map<String, ResourceResolver.ResolutionResult> fhirFromDependencies,
            Set<String> referencedPaths
    ) {}

    /**
     * Discovery result that always contains a Map of plugins.
     */
    public record DiscoveryResult(
            Map<String, PluginDiscovery> plugins,
            File sharedResourcesDir,
            Set<ApiVersion> detectedVersions
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
        this.apiVersionDetector = new ApiVersionDetector();
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

        if (pluginDiscovery.getAllPlugins().isEmpty()) {
            throw new IllegalStateException("""
                    No ProcessPluginDefinition implementations found
                    Possible causes:
                      - The project is not set up correctly as a DSF Process Plugin project
                      - You try to lint multi-module project, but currently, the linter does not fully support that
                      - Try running the linter again with the --mvn option (for more information, see the README.md file)
                      - Build a JAR for the project you want to lint and pass that JAR to the linter
                      If it still doesn’t work, please report the issue in a pull request on GitHub.""");
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

        return new DiscoveryResult(plugins, sharedResourcesDir, detectedVersions);
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

        ApiVersion apiVersion = detectPluginApiVersion(adapter, projectPath);

        File pluginSpecificRoot = resolvePluginSpecificResourceRoot(adapter, sharedResourcesDir);

        logger.debug("Plugin-specific resource root: " + pluginSpecificRoot.getAbsolutePath());

        Set<String> referencedBpmnPaths = ResourceDiscoveryUtils.collectBpmnPaths(adapter);
        Set<String> referencedFhirPaths = ResourceDiscoveryUtils.collectFhirPaths(adapter);

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
     * Detects API version for a specific plugin.
     */
    private ApiVersion detectPluginApiVersion(PluginDefinitionDiscovery.PluginAdapter adapter, Path projectPath)
            throws MissingServiceRegistrationException {

        if (adapter instanceof GenericPluginAdapter generic) {
            GenericPluginAdapter.ApiVersion version = generic.getApiVersion();
            ApiVersion mappedVersion = version == GenericPluginAdapter.ApiVersion.V2
                    ? ApiVersion.V2
                    : ApiVersion.V1;
            logger.debug("Plugin uses API " + mappedVersion + " (determined by adapter)");
            return mappedVersion;
        }

        logger.debug("Adapter is not GenericPluginAdapter, falling back to detector");
        return apiVersionDetector.detect(projectPath)
                .map(DetectedVersion::version)
                .orElseGet(() -> {
                    logger.warn("Could not detect API version. Using UNKNOWN.");
                    return ApiVersion.UNKNOWN;
                });
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