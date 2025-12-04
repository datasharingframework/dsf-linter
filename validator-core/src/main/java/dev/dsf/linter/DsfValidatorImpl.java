package dev.dsf.linter;

import dev.dsf.linter.analysis.LeftoverResourceDetector;
import dev.dsf.linter.exception.MissingServiceRegistrationException;
import dev.dsf.linter.exception.ResourceValidationException;
import dev.dsf.linter.item.AbstractValidationItem;
import dev.dsf.linter.item.PluginValidationItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.report.ValidationReportGenerator;
import dev.dsf.linter.service.*;
import dev.dsf.linter.setup.ProjectSetupHandler;
import dev.dsf.linter.util.laoder.ClassLoaderUtils;
import dev.dsf.linter.util.validation.ValidationUtils;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.validation.ValidationOutput;
import dev.dsf.linter.service.ValidationResult;

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
    ) {
    }

    /**
     * Validation result for a single plugin
     */
    public record PluginValidation(
            String pluginName,
            String pluginClass,
            ApiVersion apiVersion,
            ValidationOutput output,
            Path reportPath
    ) {
    }

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

        try {
            // Phase 1: Project Setup
            reportGenerator.printPhaseHeader("Phase 1: Project Setup");
            ProjectSetupHandler.ProjectContext context = setupHandler.setupValidationEnvironment(config.projectPath());

            // Execute all validation phases with temporary context classloader
            return ClassLoaderUtils.withTemporaryContextClassLoader(context.projectClassLoader(), () -> {
                try {
                    // Phase 2: Resource Discovery
                    reportGenerator.printPhaseHeader("Phase 2: Resource Discovery");
                    ResourceDiscoveryService.DiscoveryResult discovery = discoveryService.discover(context);

                    if (discovery.plugins().isEmpty()) {
                        logger.warn("No plugins found. Nothing to validate.");
                        return new OverallValidationResult(
                                Collections.emptyMap(),
                                null,
                                config.reportPath(),
                                System.currentTimeMillis() - startTime,
                                true
                        );
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
                    int totalPluginErrors = validations.values().stream()
                            .mapToInt(v -> v.output().getErrorCount())
                            .sum();
                    boolean success = !config.failOnErrors() || (totalPluginErrors == 0);

                    return new OverallValidationResult(
                            validations,
                            leftoverResults,
                            config.reportPath(),
                            executionTime,
                            success
                    );

                } catch (ResourceValidationException | MissingServiceRegistrationException e) {
                    logger.error("FATAL: Validation failed: " + e.getMessage(), e);
                    throw new IOException("Validation failed", e);
                } finally {
                    // Cleanup API version holder
                    ApiVersionHolder.clear();
                    logger.debug("ApiVersionHolder cleared.");
                }
            });

        } catch (IOException e) {
            throw e; // Re-throw IOException as-is
        } catch (Exception e) {
            logger.error("FATAL: Validation failed with unexpected error: " + e.getMessage(), e);
            throw new IOException("Validation failed", e);
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
     * Validates all discovered plugins uniformly (works for one or many).
     * Prints a clean, fixed console structure per plugin:
     * BPMN → FHIR → Plugin (always shown, even when zero issues),
     * then prints the per-plugin summary.
     */
    private Map<String, PluginValidation> validateAllPlugins(
            ProjectSetupHandler.ProjectContext context,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverAnalysis)
            throws ResourceValidationException, IOException, MissingServiceRegistrationException {

        Map<String, PluginValidation> validations = new LinkedHashMap<>();

        final boolean isSinglePluginProject = (discovery.plugins().size() == 1);
        int currentPluginIndex = 0;
        final int totalPlugins = discovery.plugins().size();

        for (Map.Entry<String, ResourceDiscoveryService.PluginDiscovery> entry : discovery.plugins().entrySet()) {
            final String pluginName = entry.getKey();
            final ResourceDiscoveryService.PluginDiscovery plugin = entry.getValue();

            currentPluginIndex++;
            final boolean isLastPlugin = (currentPluginIndex == totalPlugins);

            // Phase header for this plugin
            reportGenerator.printPluginHeader(pluginName, currentPluginIndex, totalPlugins);

            // Ensure downstream validators know the API version
            ApiVersionHolder.setVersion(plugin.apiVersion());

            ValidationItemsCollection itemsCollection = collectValidationItems(pluginName, plugin);
            // 2) Run Plugin validation (ServiceLoader etc.) on the items collected above
            ValidationResult pluginResult = pluginValidator.validatePlugin(
                    context.projectPath(),
                    plugin.adapter(),
                    plugin.apiVersion(),
                    itemsCollection.pluginLevelItems
            );

            // 3) Leftover analysis items (project-wide, but attributed per plugin)
            List<AbstractValidationItem> leftoverItems = leftoverDetector.getItemsForPlugin(
                    leftoverAnalysis,
                    pluginName,
                    plugin,
                    isLastPlugin,
                    isSinglePluginProject
            );

            // Build the three strictly separated groups used for console sections
            List<AbstractValidationItem> bpmnNonSuccess = itemsCollection.getBpmnNonSuccessItems();
            List<AbstractValidationItem> fhirNonSuccess = itemsCollection.getFhirNonSuccessItems();
            List<AbstractValidationItem> pluginNonSuccess = getPluginNonSuccessItems(pluginResult, leftoverItems);

            String pluginNameShort = plugin.adapter().sourceClass().getSimpleName();
            reportGenerator.printValidationSections(
                    bpmnNonSuccess,
                    fhirNonSuccess,
                    pluginNonSuccess,
                    pluginResult,
                    pluginNameShort
            );

            PluginValidation pluginValidation = buildPluginValidationResult(
                    pluginName,
                    plugin,
                    itemsCollection,
                    pluginResult,
                    leftoverItems
            );

            validations.put(pluginName, pluginValidation);

            reportGenerator.printPluginSummary(pluginValidation.output());
        }

        return validations;
    }

    /**
     * Collects validation items from BPMN and FHIR validators.
     * Separates plugin-level items from pure BPMN/FHIR items.
     *
     * @param pluginName The name of the plugin
     * @param plugin     The plugin discovery information
     * @return A collection containing all validation items
     */
    private ValidationItemsCollection collectValidationItems(
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin)
            throws ResourceValidationException, IOException {

        ValidationResult bpmnResult = bpmnValidator.validate(
                pluginName,
                plugin.bpmnFiles(),
                plugin.missingBpmnRefs()
        );

        ValidationResult fhirResult = fhirValidator.validate(
                pluginName,
                plugin.fhirFiles(),
                plugin.missingFhirRefs()
        );

        List<AbstractValidationItem> allValidationItems = new ArrayList<>(bpmnResult.getItems());
        allValidationItems.addAll(fhirResult.getItems());

        List<AbstractValidationItem> pluginLevelItems = allValidationItems.stream()
                .filter(i -> i instanceof PluginValidationItem)
                .toList();

        List<AbstractValidationItem> nonPluginItems = allValidationItems.stream()
                .filter(i -> !(i instanceof PluginValidationItem))
                .toList();

        return new ValidationItemsCollection(nonPluginItems, pluginLevelItems);
    }

    /**
     * Builds the final PluginValidation result for a single plugin.
     *
     * @param pluginName       The name of the plugin
     * @param plugin           The plugin discovery information
     * @param itemsCollection  The collected validation items
     * @param pluginResult     The plugin validation result
     * @param leftoverItems    The leftover resource items
     * @return The complete PluginValidation result
     */
    private PluginValidation buildPluginValidationResult(
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin,
            ValidationItemsCollection itemsCollection,
            ValidationResult pluginResult,
            List<AbstractValidationItem> leftoverItems) throws IOException {

        List<AbstractValidationItem> finalValidationItems = new ArrayList<>();
        finalValidationItems.addAll(itemsCollection.nonPluginItems);
        finalValidationItems.addAll(pluginResult.getItems());
        if (leftoverItems != null && !leftoverItems.isEmpty()) {
            finalValidationItems.addAll(leftoverItems);
        }

        ValidationOutput finalOutput = new ValidationOutput(finalValidationItems);

        Path pluginReportPath = config.reportPath().resolve(pluginName);
        Files.createDirectories(pluginReportPath);

        return new PluginValidation(
                pluginName,
                plugin.adapter().sourceClass().getName(),
                plugin.apiVersion(),
                finalOutput,
                pluginReportPath
        );
    }

    /**
     * Gets non-SUCCESS plugin items including leftover items.
     *
     * @param pluginResult  The plugin validation result
     * @param leftoverItems The leftover resource items
     * @return List of non-SUCCESS plugin items
     */
    private List<AbstractValidationItem> getPluginNonSuccessItems(
            ValidationResult pluginResult,
            List<AbstractValidationItem> leftoverItems)  {

        List<AbstractValidationItem> allPluginItemsForDisplay = new ArrayList<>(pluginResult.getItems());
        if (leftoverItems != null && !leftoverItems.isEmpty()) {
            allPluginItemsForDisplay.addAll(leftoverItems);
        }

        return ValidationUtils.onlyPluginItems(allPluginItemsForDisplay).stream()
                .filter(i -> i.getSeverity() != ValidationSeverity.SUCCESS)
                .toList();
    }

    /**
     * Internal record to hold separated validation items.
     */
    private record ValidationItemsCollection(
            List<AbstractValidationItem> nonPluginItems,
            List<AbstractValidationItem> pluginLevelItems
    ) {
        List<AbstractValidationItem> getBpmnNonSuccessItems() {
            return ValidationUtils.onlyBpmnItems(nonPluginItems).stream()
                    .filter(i -> i.getSeverity() != ValidationSeverity.SUCCESS)
                    .toList();
        }

        List<AbstractValidationItem> getFhirNonSuccessItems() {
            return ValidationUtils.onlyFhirItems(nonPluginItems).stream()
                    .filter(i -> i.getSeverity() != ValidationSeverity.SUCCESS)
                    .toList();
        }
    }
}