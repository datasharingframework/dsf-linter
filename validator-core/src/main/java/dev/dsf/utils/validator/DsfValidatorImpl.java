package dev.dsf.utils.validator;

import dev.dsf.utils.validator.analysis.LeftoverResourceDetector;
import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.PluginValidationItem;
import dev.dsf.utils.validator.logger.LogDecorators;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.report.ValidationReportGenerator;
import dev.dsf.utils.validator.service.*;
import dev.dsf.utils.validator.setup.ProjectSetupHandler;
import dev.dsf.utils.validator.util.Console;
import dev.dsf.utils.validator.util.ValidationUtils;
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
     * Validates all discovered plugins uniformly (works for one or many).
     * Prints a clean, fixed console structure per plugin:
     *   BPMN → FHIR → Plugin (always shown, even when zero issues),
     *   then prints the per-plugin summary.
     */
    private Map<String, PluginValidation> validateAllPlugins(
            ProjectSetupHandler.ProjectContext context,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverAnalysis)
            throws ResourceValidationException, IOException, MissingServiceRegistrationException
    {
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

            // ---------------------------------------------------------------------
            // 1) Run BPMN + FHIR validators (they may also contribute Plugin items)
            // ---------------------------------------------------------------------
            ValidationResult bpmnResult = bpmnValidator.validate(pluginName, plugin.bpmnFiles(), plugin.missingBpmnRefs());
            ValidationResult fhirResult = fhirValidator.validate(pluginName, plugin.fhirFiles(), plugin.missingFhirRefs());

            // Collect all items emitted by BPMN + FHIR passes
            List<AbstractValidationItem> allValidationItems = new ArrayList<>(bpmnResult.getItems());
            allValidationItems.addAll(fhirResult.getItems());

            // Split into plugin-level vs. pure BPMN/FHIR items
            List<AbstractValidationItem> pluginLevelItems = allValidationItems.stream()
                    .filter(i -> i instanceof PluginValidationItem)
                    .toList();

            List<AbstractValidationItem> nonPluginItems = allValidationItems.stream()
                    .filter(i -> !(i instanceof PluginValidationItem))
                    .toList();

            // ---------------------------------------------------------------------
            // 2) Run Plugin validation (ServiceLoader etc.) on the items collected above
            // ---------------------------------------------------------------------
            PluginValidationService.ValidationResult pluginResult = pluginValidator.validatePlugin(
                    context.projectPath(),
                    plugin.adapter(),
                    plugin.apiVersion(),
                    pluginLevelItems
            );

            // ---------------------------------------------------------------------
            // 3) Leftover analysis items (project-wide, but attributed per plugin)
            // ---------------------------------------------------------------------
            List<AbstractValidationItem> leftoverItems = leftoverDetector.getItemsForPlugin(
                    leftoverAnalysis,
                    pluginName,
                    plugin,
                    isLastPlugin,
                    isSinglePluginProject
            );

            // Build the three strictly separated groups used for console sections
            List<AbstractValidationItem> bpmnNonSuccess = ValidationUtils.onlyBpmnItems(nonPluginItems).stream()
                    .filter(i -> i.getSeverity() != ValidationSeverity.SUCCESS)
                    .toList();

            List<AbstractValidationItem> fhirNonSuccess = ValidationUtils.onlyFhirItems(nonPluginItems).stream()
                    .filter(i -> i.getSeverity() != ValidationSeverity.SUCCESS)
                    .toList();

            // Plugin = pluginResult (+ optional leftovers), then filter non-success
            List<AbstractValidationItem> allPluginItemsForDisplay = new ArrayList<>(pluginResult.getItems());
            if (leftoverItems != null && !leftoverItems.isEmpty()) {
                allPluginItemsForDisplay.addAll(leftoverItems);
            }
            List<AbstractValidationItem> pluginNonSuccess = ValidationUtils.onlyPluginItems(allPluginItemsForDisplay).stream()
                    .filter(i -> i.getSeverity() != ValidationSeverity.SUCCESS)
                    .toList();

            // 4) Print sections in fixed order: BPMN → FHIR → Plugin
            {
                SeverityCount bpmnCount = countSeverities(bpmnNonSuccess);
                LogDecorators.infoMint(logger,
                        "Found " + bpmnCount.total + " BPMN issue(s): (" +
                                bpmnCount.errors + " errors, " +
                                bpmnCount.warnings + " warnings, " +
                                bpmnCount.infos + " infos)");

                if (!bpmnNonSuccess.isEmpty()) {
                    printItemsByType(bpmnNonSuccess, "BPMN");
                }
            }

            {
                SeverityCount fhirCount = countSeverities(fhirNonSuccess);
                LogDecorators.infoMint(logger,
                        "Found " + fhirCount.total + " FHIR issue(s): (" +
                                fhirCount.errors + " errors, " +
                                fhirCount.warnings + " warnings, " +
                                fhirCount.infos + " infos)");


                if (!fhirNonSuccess.isEmpty()) {
                    printItemsByType(fhirNonSuccess, "FHIR");
                }
            }

            final String pluginNameShort = plugin.adapter().sourceClass().getSimpleName();
            {
                SeverityCount pluginCount = countSeverities(pluginNonSuccess);
                LogDecorators.infoMint(logger,
                        "Found " + pluginCount.total + " Plugin issue(s) for: " + pluginNameShort +
                                ": (" + pluginCount.errors + " errors, " +
                                pluginCount.warnings + " warnings, " +
                                pluginCount.infos + " infos)");


                if (!pluginNonSuccess.isEmpty()) {
                    printItemsByType(pluginNonSuccess, "Plugin");
                }

                boolean hasServiceLoaderSuccess =
                        pluginResult.getItems().stream()
                                .anyMatch(i -> i.getClass().getSimpleName().equals("PluginDefinitionValidationItemSuccess"));

                boolean hasServiceLoaderMissing =
                        pluginResult.getItems().stream()
                                .anyMatch(i -> i.getClass().getSimpleName().equals("PluginDefinitionMissingServiceLoaderRegistrationValidationItem"));

                if (hasServiceLoaderSuccess) {
                    Console.green("✓ ServiceLoader registration verified");
                } else if (hasServiceLoaderMissing) {
                    Console.red("✗ ServiceLoader registration missing");
                } else {
                    // Fallback: keep a visible line to satisfy 'always visible'
                    Console.yellow("⚠ ServiceLoader registration: not verified explicitly");
                }
            }

            // 5) Build final output for this plugin (summary counts include SUCCESS)
            List<AbstractValidationItem> finalValidationItems = new ArrayList<>();
            finalValidationItems.addAll(nonPluginItems);           // pure BPMN/FHIR items
            finalValidationItems.addAll(pluginResult.getItems());  // plugin-level items
            if (leftoverItems != null && !leftoverItems.isEmpty()) // leftover plugin items (if any)
                finalValidationItems.addAll(leftoverItems);

            ValidationOutput finalOutput = new ValidationOutput(finalValidationItems);

            // Prepare report folder per plugin (used by HTML report generator)
            Path pluginReportPath = config.reportPath().resolve(pluginName);
            Files.createDirectories(pluginReportPath);

            // Store result for later reporting/summary
            validations.put(pluginName, new PluginValidation(
                    pluginName,
                    plugin.adapter().sourceClass().getName(),
                    plugin.apiVersion(),
                    finalOutput,
                    pluginReportPath
            ));

            // Print the per-plugin summary (✓/⚠/✓/✓) using your ReportGenerator
            reportGenerator.printPluginSummary(finalOutput);
        }

        return validations;
    }


    /**
     * Prints validation items grouped by severity for a specific type (BPMN, FHIR, or Plugin).
     * Only prints non-SUCCESS items.
     *
     * @param items The items to print
     * @param typeName The name of the type (e.g., "BPMN", "FHIR", "Plugin")
     */
    private void printItemsByType(List<AbstractValidationItem> items, String typeName) {
        // Filter out SUCCESS items
        List<AbstractValidationItem> nonSuccessItems = items.stream()
                .filter(item -> item.getSeverity() != ValidationSeverity.SUCCESS)
                .toList();

        if (nonSuccessItems.isEmpty()) {
            return;
        }

        // Group by severity
        List<AbstractValidationItem> errors = ValidationUtils.filterBySeverity(nonSuccessItems, ValidationSeverity.ERROR);
        List<AbstractValidationItem> warnings = ValidationUtils.filterBySeverity(nonSuccessItems, ValidationSeverity.WARN);
        List<AbstractValidationItem> infos = ValidationUtils.filterBySeverity(nonSuccessItems, ValidationSeverity.INFO);

        // Print ERROR items
        if (!errors.isEmpty()) {
            Console.red("  ✗ ERROR items:");
            for (AbstractValidationItem item : errors) {
                Console.red("    * " + formatItemMessage(item));
            }
        }

        // Print WARN items
        if (!warnings.isEmpty()) {
            Console.yellow("  ⚠ WARN items:");
            for (AbstractValidationItem item : warnings) {
                Console.yellow("    * " + formatItemMessage(item));
            }
        }

        // Print INFO items
        if (!infos.isEmpty()) {
            logger.info("  ℹ INFO items:");
            for (AbstractValidationItem item : infos) {
                logger.info("    * " + formatItemMessage(item));
            }
        }
    }

    /**
     * Formats a validation item message for display.
     * Removes redundant severity prefix if present in toString().
     */
    private String formatItemMessage(AbstractValidationItem item) {
        String message = item.toString();
        String severityPrefix = "[" + item.getSeverity() + "] ";
        if (message.startsWith(severityPrefix)) {
            message = message.substring(severityPrefix.length());
        }
        return message;
    }

    /**
     * Computes the count of ERROR, WARNING, and INFO severities for a given list of validation items.
     */
    private static class SeverityCount {
        final long errors;
        final long warnings;
        final long infos;
        final long total;

        SeverityCount(long errors, long warnings, long infos) {
            this.errors = errors;
            this.warnings = warnings;
            this.infos = infos;
            this.total = errors + warnings + infos;
        }
    }

    private static SeverityCount countSeverities(List<? extends AbstractValidationItem> items) {
        long errors = items.stream().filter(i -> i.getSeverity() == ValidationSeverity.ERROR).count();
        long warnings = items.stream().filter(i -> i.getSeverity() == ValidationSeverity.WARN).count();
        long infos = items.stream().filter(i -> i.getSeverity() == ValidationSeverity.INFO).count();
        return new SeverityCount(errors, warnings, infos);
    }
}