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
 * Core linter for DSF (Data Sharing Framework) process plugins.
 * <p>
 * This class orchestrates the complete linting process for DSF plugins, including:
 * <ul>
 *   <li><b>Phase 1:</b> Project setup and Maven build execution</li>
 *   <li><b>Phase 2:</b> Resource discovery (plugins, BPMN processes, FHIR resources)</li>
 *   <li><b>Phase 3:</b> Linting of BPMN, FHIR, and plugin configurations</li>
 *   <li><b>Phase 4:</b> Report generation (HTML and/or JSON)</li>
 *   <li><b>Phase 5:</b> Summary and results</li>
 * </ul>
 * </p>
 * <p>
 * The linter supports both single-plugin and multi-plugin projects, automatically
 * detecting and validating all process plugins in the project. It validates:
 * <ul>
 *   <li>BPMN process definitions and references</li>
 *   <li>FHIR resource definitions and references</li>
 *   <li>Plugin service registrations</li>
 *   <li>Resource consistency and completeness</li>
 *   <li>Unreferenced (leftover) resources</li>
 * </ul>
 * </p>
 *
 * @see Config
 * @see PluginLinter
 * @see OverallLinterResult
 */
public class DsfLinter {

    /**
     * Configuration for the DSF Linter.
     *
     * @param projectPath the path to the project root directory
     * @param reportPath the path where linting reports should be generated
     * @param generateHtmlReport whether to generate an HTML report
     * @param generateJsonReport whether to generate a JSON report
     * @param failOnErrors whether the linter should fail (exit code 1) when errors are found
     * @param mavenGoals custom Maven goals to execute during project setup
     * @param skipGoals Maven goals to skip during project setup
     * @param logger the logger instance for output
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
     * Linting result for a single plugin.
     *
     * @param pluginName the name of the plugin
     * @param pluginClass the fully qualified class name of the plugin
     * @param apiVersion the DSF API version used by the plugin
     * @param output the detailed linting output (errors, warnings, etc.)
     * @param reportPath the path to the generated report for this plugin
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
     * Overall linting result containing results for all plugins.
     *
     * @param pluginLinter map of plugin names to their individual linting results
     * @param leftoverAnalysis analysis of unreferenced resources at project level
     * @param masterReportPath path to the master report directory
     * @param executionTimeMs total execution time in milliseconds
     * @param success whether the linting passed (no errors if failOnErrors is true)
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

    /**
     * Creates a new DSF Linter instance with the specified configuration.
     * <p>
     * Initializes all required services for linting, including:
     * <ul>
     *   <li>Project setup handler</li>
     *   <li>Resource discovery service</li>
     *   <li>BPMN linting service</li>
     *   <li>FHIR linting service</li>
     *   <li>Plugin linting service</li>
     *   <li>Leftover resource detector</li>
     *   <li>Report generator</li>
     * </ul>
     * </p>
     *
     * @param config the linter configuration
     */
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
     * Main linting entry point.
     * <p>
     * Executes the complete linting process through five phases:
     * <ol>
     *   <li>Project Setup - builds the project and prepares the environment</li>
     *   <li>Resource Discovery - discovers plugins and their resources</li>
     *   <li>Linting - validates BPMN, FHIR, and plugin configurations</li>
     *   <li>Report Generation - creates HTML and/or JSON reports</li>
     *   <li>Summary - displays results and execution time</li>
     * </ol>
     * </p>
     * <p>
     * Handles any number of plugins uniformly (single or multiple plugins).
     * Uses a temporary context classloader to ensure proper resource isolation.
     * </p>
     *
     * @return the overall linting result containing all plugin results and statistics
     * @throws IOException if project setup, resource access, or report generation fails
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
                    
                    // Consider failed plugins as errors (partial success means non-zero exit code)
                    boolean hasFailedPlugins = discovery.hasFailedPlugins();
                    boolean success = !config.failOnErrors() || (totalPluginErrors == 0 && !hasFailedPlugins);

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
     * Lints all discovered plugins uniformly (works for one or many).
     * <p>
     * Delegates the complex per-plugin orchestration to {@link PluginLintingOrchestrator}.
     * This method has two main responsibilities:
     * <ol>
     *   <li>Loop coordination - iterates over plugins and calculates context</li>
     *   <li>Result aggregation - collects linting results from all plugins</li>
     * </ol>
     * </p>
     *
     * @param context the project context containing classloader and directories
     * @param discovery the resource discovery result containing all plugins
     * @param leftoverAnalysis the project-level leftover resource analysis
     * @return map of plugin names to their linting results
     * @throws ResourceLinterException if a linting error occurs
     * @throws IOException if resource access fails
     * @throws MissingServiceRegistrationException if required service registrations are missing
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