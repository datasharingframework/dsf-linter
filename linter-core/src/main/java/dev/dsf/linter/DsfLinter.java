package dev.dsf.linter;

import dev.dsf.linter.analysis.LeftoverResourceDetector;
import dev.dsf.linter.exception.MissingServiceRegistrationException;
import dev.dsf.linter.exception.ResourceLinterException;
import dev.dsf.linter.logger.Console;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.report.LintingReportGenerator;
import dev.dsf.linter.service.*;
import dev.dsf.linter.setup.ProjectSetupHandler;
import dev.dsf.linter.util.loader.ClassLoaderUtils;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.linting.LintingOutput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Unified linter that handles any number of plugins (one or more).
 * A single plugin is simply a special case of the general multi-plugin approach.
 */
public class DsfLinter {

    /**
     * Configuration for linting
     */
    public record Config(
            Path projectPath,
            Path reportPath,
            boolean generateHtmlReport,
            boolean generateJsonReport,
            boolean failOnErrors,
            String[] mavenGoals,
            String[] skipGoals,
            Logger logger
    ) {
    }

    /**
     * linter result for a single plugin
     */
    public record PluginLinter(
            String pluginName,
            String pluginClass,
            ApiVersion apiVersion,
            LintingOutput output,
            Path reportPath
    ) {
    }

    /**
     * Overall linting result containing all plugin lints
     */
    public record OverallLinterResult(
            Map<String, PluginLinter> pluginLinter,
            LeftoverResourceDetector.AnalysisResult leftoverAnalysis,
            Path masterReportPath,
            long executionTimeMs,
            boolean success
    ) {
        /**
         * Get total error count from plugins only
         */
        public int getPluginErrors() {
            return pluginLinter.values().stream()
                    .mapToInt(v -> v.output().getErrorCount())
                    .sum();
        }

        /**
         * Get total warning count from plugins only
         */
        public int getPluginWarnings() {
            return pluginLinter.values().stream()
                    .mapToInt(v -> v.output().getWarningCount())
                    .sum();
        }

        /**
         * Get total count of leftover resources.
         * Note: These are typically treated as warnings but counted separately.
         */
        public int getLeftoverCount() {
            return leftoverAnalysis != null ? leftoverAnalysis.getTotalLeftoverCount() : 0;
        }

        /**
         * Get total error count across all plugins and project-level leftovers.
         */
        public int getTotalErrors() {
            return getPluginErrors() + getLeftoverCount();
        }
    }

    private final Config config;
    private final Logger logger;
    private final ProjectSetupHandler setupHandler;
    private final ResourceDiscoveryService discoveryService;
    private final LeftoverResourceDetector leftoverDetector;
    private final LintingReportGenerator reportGenerator;
    private final PluginLintingOrchestrator pluginOrchestrator;

    public DsfLinter(Config config) {
        this.config = config;
        this.logger = config.logger();
        Console.init(logger);
        this.setupHandler = new ProjectSetupHandler(logger);
        this.discoveryService = new ResourceDiscoveryService(logger);
        BpmnLintingService bpmnLinter = new BpmnLintingService(logger);
        FhirLintingService fhirLinter = new FhirLintingService(logger);
        PluginLintingService pluginLinter = new PluginLintingService(logger);
        this.leftoverDetector = new LeftoverResourceDetector(logger);
        this.reportGenerator = new LintingReportGenerator(logger);
        this.pluginOrchestrator = new PluginLintingOrchestrator(
                bpmnLinter,
                fhirLinter,
                pluginLinter,
                leftoverDetector,
                reportGenerator,
                config.reportPath(),
                logger
        );
    }

    /**
     * Main linter entry point.
     * Handles any number of plugins uniformly (one or more).
     */
    public OverallLinterResult lint() throws IOException {
        long startTime = System.currentTimeMillis();
        reportGenerator.printHeader(config);

        try {
            // Phase 1: Project Setup
            reportGenerator.printPhaseHeader("Phase 1: Project Setup");
            ProjectSetupHandler.ProjectContext context = setupHandler.setupLintingEnvironment(
                    config.projectPath(),
                    config.mavenGoals(),
                    config.skipGoals()
            );

            // Execute all linting phases with temporary context classloader
            return ClassLoaderUtils.withTemporaryContextClassLoader(context.projectClassLoader(), () -> {
                try {
                    // Phase 2: Resource Discovery
                    reportGenerator.printPhaseHeader("Phase 2: Resource Discovery");
                    ResourceDiscoveryService.DiscoveryResult discovery = discoveryService.discover(context);

                    if (discovery.plugins().isEmpty()) {
                        logger.warn("No plugins found. Nothing to lint.");
                        return new OverallLinterResult(
                                Collections.emptyMap(),
                                null,
                                config.reportPath(),
                                System.currentTimeMillis() - startTime,
                                true
                        );
                    }

                    // Phase 3: linting (Plugins and Project-level)
                    reportGenerator.printPhaseHeader("Phase 3: Linting");

                    // Always perform project-level leftover analysis (works for 1 or more plugins)
                    LeftoverResourceDetector.AnalysisResult leftoverResults =
                            performProjectLeftoverAnalysis(context, discovery);

                    // lint all plugins AND include leftover analysis items
                    Map<String, PluginLinter> pluginLinting = lintAllPlugins(context, discovery, leftoverResults);

                    // Phase 4: Report Generation
                    reportGenerator.printPhaseHeader("Phase 4: Report Generation");
                    reportGenerator.generateReports(pluginLinting, discovery, leftoverResults, config);

                    // Phase 5: Summary
                    long executionTime = System.currentTimeMillis() - startTime;
                    reportGenerator.printSummary(pluginLinting, discovery, leftoverResults, executionTime, config);

                    // Determine final success status
                    int totalPluginErrors = pluginLinting.values().stream()
                            .mapToInt(v -> v.output().getErrorCount())
                            .sum();
                    boolean success = !config.failOnErrors() || (totalPluginErrors == 0);

                    return new OverallLinterResult(
                            pluginLinting,
                            leftoverResults,
                            config.reportPath(),
                            executionTime,
                            success
                    );

                } catch (ResourceLinterException | MissingServiceRegistrationException e) {
                    logger.error("FATAL: Linting failed: " + e.getMessage(), e);
                    throw new IOException("Linting failed", e);
                } finally {
                    // Cleanup API version holder
                    ApiVersionHolder.clear();
                    logger.debug("ApiVersionHolder cleared.");
                }
            });

        } catch (IOException e) {
            throw e; // Re-throw IOException as-is
        } catch (Exception e) {
            logger.error("FATAL: Linting failed with unexpected error: " + e.getMessage(), e);
            throw new IOException("Linting failed", e);
        }
    }

    /**
     * Performs project-wide analysis for leftover resources by aggregating
     * all referenced paths from all discovered plugins.
     * Works uniformly for any number of plugins (one or more).
     *
     * @param context   The project context.
     * @param discovery The discovery result containing all plugins.
     * @return The result of the leftover analysis.
     */
    private LeftoverResourceDetector.AnalysisResult performProjectLeftoverAnalysis(
            ProjectSetupHandler.ProjectContext context,
            ResourceDiscoveryService.DiscoveryResult discovery) {

        logger.info("\n--- Analyzing for project-wide unreferenced resources ---");

        Set<String> allReferencedBpmn = new HashSet<>();
        Set<String> allReferencedFhir = new HashSet<>();

        // Collect all referenced paths from all plugins (works for 1 or more)
        for (ResourceDiscoveryService.PluginDiscovery plugin : discovery.plugins().values()) {
            plugin.referencedPaths().forEach(p -> {
                if (p.endsWith(".bpmn")) {
                    allReferencedBpmn.add(p);
                } else {
                    allReferencedFhir.add(p);
                }
            });
        }

        // Get the shared resources directory
        // For a single plugin, this is its resources directory
        // For multiple plugins, this is the shared resources directory
        File resourcesDir = discovery.plugins().values().iterator().next().resourcesDir();

        return leftoverDetector.analyze(
                context.projectDir(), resourcesDir, allReferencedBpmn, allReferencedFhir
        );
    }

    /**
     * lints all discovered plugins uniformly (works for one or many).
     * Delegates the complex per-plugin orchestration to PluginLintingOrchestrator.
     * This method now has only two responsibilities:
     * 1. Loop coordination (iteration, context calculation)
     * 2. Result aggregation
     */
    private Map<String, PluginLinter> lintAllPlugins(
            ProjectSetupHandler.ProjectContext context,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverAnalysis)
            throws ResourceLinterException, IOException, MissingServiceRegistrationException {

        Map<String, PluginLinter> lints = new LinkedHashMap<>();

        final boolean isSinglePluginProject = (discovery.plugins().size() == 1);
        final int totalPlugins = discovery.plugins().size();
        int currentPluginIndex = 0;

        for (Map.Entry<String, ResourceDiscoveryService.PluginDiscovery> entry : discovery.plugins().entrySet()) {
            final String pluginName = entry.getKey();
            final ResourceDiscoveryService.PluginDiscovery plugin = entry.getValue();

            currentPluginIndex++;
            final boolean isLastPlugin = (currentPluginIndex == totalPlugins);

            // Create linting context
            PluginLintingOrchestrator.PluginLintContext lintContext =
                    new PluginLintingOrchestrator.PluginLintContext(
                            currentPluginIndex,
                            totalPlugins,
                            isLastPlugin,
                            isSinglePluginProject
                    );

            // Delegate complete plugin linting to orchestrator
            PluginLinter pluginLinter = pluginOrchestrator.lintSinglePlugin(
                    pluginName,
                    plugin,
                    context,
                    leftoverAnalysis,
                    lintContext
            );

            lints.put(pluginName, pluginLinter);
        }

        return lints;
    }
}