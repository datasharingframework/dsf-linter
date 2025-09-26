package dev.dsf.utils.validator;

import dev.dsf.utils.validator.analysis.LeftoverResourceDetector;
import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.report.ValidationReportGenerator;
import dev.dsf.utils.validator.service.*;
import dev.dsf.utils.validator.setup.ProjectSetupHandler;
import dev.dsf.utils.validator.util.api.ApiVersion;
import dev.dsf.utils.validator.util.api.ApiVersionHolder;
import dev.dsf.utils.validator.util.validation.ValidationOutput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Unified validator that handles any number of plugins (one or more).
 * A single plugin is simply a special case of the general multi-plugin approach.
 */
public class DsfValidatorImpl {

    /**
     * Configuration for validation
     */
    public record Config(
            Path projectPath,
            Path reportPath,
            boolean generateHtmlReport,
            boolean failOnErrors,
            Logger logger
    )  { }

    /**
     * Validation result for a single plugin
     */
    public record PluginValidation(
            String pluginName,
            String pluginClass,
            ApiVersion apiVersion,
            ValidationOutput output,
            Path reportPath
    ) {}

    /**
     * Overall validation result containing all plugin validations
     */
    public record ValidationResult(
            Map<String, PluginValidation> pluginValidations,
            LeftoverResourceDetector.AnalysisResult leftoverAnalysis,
            Path masterReportPath,
            long executionTimeMs,
            boolean success
    ) {
        /**
         * Get total error count from plugins only
         */
        public int getPluginErrors() {
            return pluginValidations.values().stream()
                    .mapToInt(v -> v.output().getErrorCount())
                    .sum();
        }

        /**
         * Get total warning count from plugins only
         */
        public int getPluginWarnings() {
            return pluginValidations.values().stream()
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
            // For the overall success status, we treat leftovers as errors if failOnErrors is active.
            return getPluginErrors() + getLeftoverCount();
        }
    }

    private final Config config;
    private final Logger logger;
    private final ProjectSetupHandler setupHandler;
    private final ResourceDiscoveryService discoveryService;
    private final BpmnValidationService bpmnValidator;
    private final FhirValidationService fhirValidator;
    private final PluginValidationService pluginValidator;
    private final LeftoverResourceDetector leftoverDetector;
    private final ValidationReportGenerator reportGenerator;


    public DsfValidatorImpl(Config config) {
        this.config = config;
        this.logger = config.logger();
        this.setupHandler = new ProjectSetupHandler(logger);
        this.discoveryService = new ResourceDiscoveryService(logger);
        this.bpmnValidator = new BpmnValidationService(logger);
        this.fhirValidator = new FhirValidationService(logger);
        this.pluginValidator = new PluginValidationService(logger);
        this.leftoverDetector = new LeftoverResourceDetector(logger);
        this.reportGenerator = new ValidationReportGenerator(logger);
    }

    /**
     * Main validation entry point.
     * Handles any number of plugins uniformly (one or more).
     */
    public ValidationResult validate() throws IOException {
        long startTime = System.currentTimeMillis();
        printHeader();

        final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            // Phase 1: Project Setup
            logger.info("\n--- Phase 1: Project Setup ---");
            ProjectSetupHandler.ProjectContext context = setupHandler.setupValidationEnvironment(config.projectPath());

            // Phase 2: Resource Discovery
            logger.info("\n--- Phase 2: Resource Discovery ---");
            ResourceDiscoveryService.DiscoveryResult discovery =
                    discoveryService.discover(context);

            if (discovery.plugins().isEmpty()) {
                logger.warn("No plugins found. Nothing to validate.");
                return new ValidationResult(Collections.emptyMap(), null, config.reportPath(),
                        System.currentTimeMillis() - startTime, true);
            }

            // Phase 3: Validation (Plugins and Project-level)
            logger.info("\n--- Phase 3: Validation ---");

            // Always perform project-level leftover analysis (works for 1 or more plugins)
            LeftoverResourceDetector.AnalysisResult leftoverResults =
                    performProjectLeftoverAnalysis(context, discovery);

            // Validate all plugins (works for 1 or more)
            Map<String, PluginValidation> validations = validateAllPlugins(context, discovery);

            // Phase 4: Report Generation
            logger.info("\n--- Phase 4: Report Generation ---");
            reportGenerator.generateReports(validations, discovery, leftoverResults, config);

            // Phase 5: Summary
            long executionTime = System.currentTimeMillis() - startTime;
            printSummary(validations, discovery, leftoverResults, executionTime);

            // Determine final success status
            int totalPluginErrors = validations.values().stream().mapToInt(v -> v.output().getErrorCount()).sum();
            boolean success = !config.failOnErrors() || (totalPluginErrors == 0);

            return new ValidationResult(validations, leftoverResults, config.reportPath(), executionTime, success);

        } catch (Exception e) {
            logger.error("FATAL: Validation failed with unexpected error: " + e.getMessage(), e);
            throw new IOException("Validation failed", e);
        } finally {
            setupHandler.restoreClassLoader(previousClassLoader);
            ApiVersionHolder.clear();
            logger.debug("ClassLoader context restored and ApiVersionHolder cleared.");
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
     * Validates all discovered plugins uniformly.
     * Works the same way for one plugin or many plugins.
     */
    private Map<String, PluginValidation> validateAllPlugins(
            ProjectSetupHandler.ProjectContext context,
            ResourceDiscoveryService.DiscoveryResult discovery)
            throws ResourceValidationException, IOException, MissingServiceRegistrationException {

        Map<String, PluginValidation> validations = new LinkedHashMap<>();

        for (Map.Entry<String, ResourceDiscoveryService.PluginDiscovery> entry : discovery.plugins().entrySet()) {
            String pluginName = entry.getKey();
            ResourceDiscoveryService.PluginDiscovery plugin = entry.getValue();

            logger.info("\n--- Validating Plugin: " + pluginName + " ---");
            ApiVersionHolder.setVersion(plugin.apiVersion());

            // Core validation for BPMN, FHIR, and Plugin structure
            List<AbstractValidationItem> validationItems = new ArrayList<>();
            validationItems.addAll(bpmnValidator.validate(plugin.bpmnFiles(), plugin.missingBpmnRefs()).getItems());
            validationItems.addAll(fhirValidator.validate(plugin.fhirFiles(), plugin.missingFhirRefs()).getItems());
            validationItems.addAll(pluginValidator.validatePlugin(context.projectPath(), plugin.adapter(), plugin.apiVersion()).getItems());
            validationItems.addAll(PluginMetadataValidator.validatePluginMetadata(plugin.adapter(), config.projectPath()));

            ValidationOutput finalOutput = new ValidationOutput(validationItems);

            Path pluginReportPath = config.reportPath().resolve(pluginName);
            Files.createDirectories(pluginReportPath);

            validations.put(pluginName, new PluginValidation(
                    pluginName,
                    plugin.adapter().sourceClass().getName(),
                    plugin.apiVersion(),
                    finalOutput,
                    pluginReportPath
            ));
        }

        return validations;
    }

    private void printHeader() {
        logger.info("=".repeat(60));
        logger.info("DSF Plugin Validation");
        logger.info("=".repeat(60));
        logger.info("Project: " + config.projectPath());
        logger.info("Report:  " + config.reportPath());
        logger.info("=".repeat(60));
    }

    private void printSummary(
            Map<String, PluginValidation> validations,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            long executionTime) {

        logger.info("");
        logger.info("=".repeat(60));
        logger.info("Validation Complete!");

        var stats = discovery.getStatistics();
        logger.info(String.format(
                "- Resources Found: %d BPMN, %d FHIR (%d missing BPMN, %d missing FHIR)",
                stats.bpmnFiles(), stats.fhirFiles(),
                stats.missingBpmn(), stats.missingFhir()
        ));

        logger.info(String.format("- Plugins Found:   %d", validations.size()));

        int totalPluginErrors = validations.values().stream().mapToInt(v -> v.output().getErrorCount()).sum();
        int totalPluginWarnings = validations.values().stream().mapToInt(v -> v.output().getWarningCount()).sum();
        int totalLeftovers = (leftoverResults != null) ? leftoverResults.getTotalLeftoverCount() : 0;

        logger.info(String.format("- Plugin Errors:   %d", totalPluginErrors));
        logger.info(String.format("- Plugin Warnings: %d", totalPluginWarnings));

        if (totalLeftovers > 0) {
            logger.warn(String.format("- Unreferenced:    %d files (project-wide)", totalLeftovers));
        }

        logger.info(String.format("- Time:            %.2f seconds", executionTime / 1000.0));
        logger.info(String.format("- Reports:         %s", config.reportPath().toAbsolutePath()));
        logger.info("=".repeat(60));

        if (config.failOnErrors() && totalPluginErrors > 0) {
            logger.error("Result: FAILED (" + totalPluginErrors + " plugin errors found)");
        } else {
            logger.info("Result: SUCCESS");
        }
    }
}