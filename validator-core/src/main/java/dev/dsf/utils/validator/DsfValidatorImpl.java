package dev.dsf.utils.validator;

import dev.dsf.utils.validator.analysis.LeftoverResourceDetector;
import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.PluginValidationItem;
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
    public record OverallValidationResult(
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
    public OverallValidationResult validate() throws IOException {
        long startTime = System.currentTimeMillis();
        reportGenerator.printHeader(config);

        final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            // Phase 1: Project Setup
            reportGenerator.printPhaseHeader("Phase 1: Project Setup");
            ProjectSetupHandler.ProjectContext context = setupHandler.setupValidationEnvironment(config.projectPath());

            // Phase 2: Resource Discovery
            reportGenerator.printPhaseHeader("Phase 2: Resource Discovery");
            ResourceDiscoveryService.DiscoveryResult discovery =
                    discoveryService.discover(context);

            if (discovery.plugins().isEmpty()) {
                logger.warn("No plugins found. Nothing to validate.");
                return new OverallValidationResult(Collections.emptyMap(), null, config.reportPath(),
                        System.currentTimeMillis() - startTime, true);
            }

            // Phase 3: Validation (Plugins and Project-level)
            reportGenerator.printPhaseHeader("Phase 3: Validation");

            // Always perform project-level leftover analysis (works for 1 or more plugins)
            LeftoverResourceDetector.AnalysisResult leftoverResults =
                    performProjectLeftoverAnalysis(context, discovery);

            // Validate all plugins AND include leftover analysis items
            Map<String, PluginValidation> validations = validateAllPlugins(context, discovery, leftoverResults);

            // Phase 4: Report Generation
            reportGenerator.printPhaseHeader("Phase 4: Report Generation");
            reportGenerator.generateReports(validations, discovery, leftoverResults, config);

            // Phase 5: Summary
            long executionTime = System.currentTimeMillis() - startTime;
            reportGenerator.printSummary(validations, discovery, leftoverResults, executionTime, config);

            // Determine final success status
            int totalPluginErrors = validations.values().stream().mapToInt(v -> v.output().getErrorCount()).sum();
            boolean success = !config.failOnErrors() || (totalPluginErrors == 0);

            return new OverallValidationResult(validations, leftoverResults, config.reportPath(), executionTime, success);

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
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverAnalysis)
            throws ResourceValidationException, IOException, MissingServiceRegistrationException {

        Map<String, PluginValidation> validations = new LinkedHashMap<>();

        boolean isSinglePluginProject = (discovery.plugins().size() == 1);

        int currentPluginIndex = 0;
        int totalPlugins = discovery.plugins().size();

        for (Map.Entry<String, ResourceDiscoveryService.PluginDiscovery> entry : discovery.plugins().entrySet()) {
            String pluginName = entry.getKey();
            ResourceDiscoveryService.PluginDiscovery plugin = entry.getValue();
            currentPluginIndex++;
            boolean isLastPlugin = (currentPluginIndex == totalPlugins);

            reportGenerator.printPluginHeader(pluginName, currentPluginIndex, totalPlugins);
            ApiVersionHolder.setVersion(plugin.apiVersion());

            // Core validation for BPMN and FHIR

            // Validate BPMN (includes both BPMN items and Plugin items)
            ValidationResult bpmnResult = bpmnValidator.validate(pluginName, plugin.bpmnFiles(), plugin.missingBpmnRefs());
            List<AbstractValidationItem> allValidationItems = new ArrayList<>(bpmnResult.getItems());

            // Validate FHIR (includes both FHIR items and Plugin items)
            ValidationResult fhirResult = fhirValidator.validate(pluginName, plugin.fhirFiles(), plugin.missingFhirRefs());
            allValidationItems.addAll(fhirResult.getItems());

            // Separate Plugin-level items from BPMN/FHIR items
            List<AbstractValidationItem> pluginLevelItems = allValidationItems.stream()
                    .filter(item -> item instanceof PluginValidationItem)
                    .toList();

            // Items that are NOT Plugin-level (pure BPMN/FHIR items)
            List<AbstractValidationItem> nonPluginItems = allValidationItems.stream()
                    .filter(item -> !(item instanceof PluginValidationItem))
                    .toList();

            // Validate Plugin configuration, passing the collected Plugin-level items
            PluginValidationService.ValidationResult pluginResult = pluginValidator.validatePlugin(
                    context.projectPath(),
                    plugin.adapter(),
                    plugin.apiVersion(),
                    pluginLevelItems  // Pass the collected Plugin items
            );

            // Merge all items
            List<AbstractValidationItem> finalValidationItems = new ArrayList<>();
            finalValidationItems.addAll(nonPluginItems);  // Pure BPMN/FHIR items
            finalValidationItems.addAll(pluginResult.getItems());  // Plugin items (including collected ones)
            finalValidationItems.addAll(PluginMetadataValidator.validatePluginMetadata(plugin.adapter(), config.projectPath()));

            // Get leftover items for this plugin
            List<AbstractValidationItem> leftoverItems = leftoverDetector.getItemsForPlugin(
                    leftoverAnalysis,
                    pluginName,
                    plugin,
                    isLastPlugin,
                    isSinglePluginProject
            );

            if (!leftoverItems.isEmpty()) {
                logger.debug("Adding " + leftoverItems.size() + " leftover items to plugin: " + pluginName);

                // Print leftover resources separately BEFORE adding to validation items
                reportGenerator.printLeftoverResources(leftoverItems);

                finalValidationItems.addAll(leftoverItems);
            }

            ValidationOutput finalOutput = new ValidationOutput(finalValidationItems);

            Path pluginReportPath = config.reportPath().resolve(pluginName);
            Files.createDirectories(pluginReportPath);

            validations.put(pluginName, new PluginValidation(
                    pluginName,
                    plugin.adapter().sourceClass().getName(),
                    plugin.apiVersion(),
                    finalOutput,
                    pluginReportPath
            ));

            reportGenerator.printPluginSummary(finalOutput);
        }

        return validations;
    }
}