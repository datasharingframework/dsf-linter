package dev.dsf.linter.service;

import dev.dsf.linter.DsfLinter;
import dev.dsf.linter.exception.ResourceLinterException;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.analysis.LeftoverResourceDetector;
import dev.dsf.linter.exception.MissingServiceRegistrationException;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.output.item.PluginLintItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.report.LintingReportGenerator;
import dev.dsf.linter.setup.ProjectSetupHandler;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.util.linting.LintingUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the complete validation process for a single plugin.
 * Enhanced with resource root validation and dependency JAR support.
 */
public class PluginLintingOrchestrator {

    private final BpmnLintingService bpmnValidator;
    private final FhirLintingService fhirValidator;
    private final PluginLintingService pluginValidator;
    private final LeftoverResourceDetector leftoverDetector;
    private final LintingReportGenerator reportGenerator;
    private final Path reportBasePath;

    /**
     * Context information for validating a plugin in a multi-plugin environment.
     */
    public record PluginLintContext(
            int pluginIndex,
            int totalPlugins,
            boolean isLastPlugin,
            boolean isSinglePluginProject
    ) {}

    public PluginLintingOrchestrator(
            BpmnLintingService bpmnValidator,
            FhirLintingService fhirValidator,
            PluginLintingService pluginValidator,
            LeftoverResourceDetector leftoverDetector,
            LintingReportGenerator reportGenerator,
            Path reportBasePath,
            Logger logger) {
        this.bpmnValidator = bpmnValidator;
        this.fhirValidator = fhirValidator;
        this.pluginValidator = pluginValidator;
        this.leftoverDetector = leftoverDetector;
        this.reportGenerator = reportGenerator;
        this.reportBasePath = reportBasePath;
    }

    /**
     * lints a single plugin completely with enhanced resource root validation
     * and dependency JAR support.
     *
     * @param pluginName The unique name of the plugin
     * @param plugin The plugin discovery information
     * @param context The project context
     * @param leftoverAnalysis The project-wide leftover analysis result
     * @param validationContext Context about the plugin's position in the validation sequence
     * @return Complete linting result for this plugin
     */
    public DsfLinter.PluginLinter lintSinglePlugin(
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin,
            ProjectSetupHandler.ProjectContext context,
            LeftoverResourceDetector.AnalysisResult leftoverAnalysis,
            PluginLintContext validationContext)
            throws ResourceLinterException, IOException, MissingServiceRegistrationException {

        // Step 1: Set API version for downstream validators
        ApiVersionHolder.setVersion(plugin.apiVersion());

        // Step 2: Print plugin header
        reportGenerator.printPluginHeader(
                pluginName,
                validationContext.pluginIndex(),
                validationContext.totalPlugins()
        );

        // Step 3: Collect BPMN and FHIR Lint Items (including dependency resources)
        LintingItemsCollection itemsCollection = collectLintingItems(pluginName, plugin);

        // Step 4: Run plugin-level validation
        LintingResult pluginResult = pluginValidator.lintPlugin(
                context.projectPath(),
                plugin.adapter(),
                plugin.apiVersion(),
                itemsCollection.pluginLevelItems
        );

        // Step 4.5: Run plugin metadata validation
        List<AbstractLintItem> metadataItems = PluginMetadataLinter.lintPluginMetadata(
                plugin.adapter(),
                context.projectPath()
        );

        // Step 5: Get leftover items for this plugin
        List<AbstractLintItem> leftoverItems = leftoverDetector.getItemsForPlugin(
                leftoverAnalysis,
                pluginName,
                plugin,
                validationContext.isLastPlugin(),
                validationContext.isSinglePluginProject()
        );

        // Step 6: Group and filter items for console display
        GroupedLintingItems groupedItems = groupAndFilterItems(
                itemsCollection,
                pluginResult,
                metadataItems,
                leftoverItems
        );

        // Step 7: Print validation sections (BPMN → FHIR → Plugin)
        String pluginNameShort = plugin.adapter().sourceClass().getSimpleName();
        reportGenerator.printLintSections(
                groupedItems.bpmnNonSuccess,
                groupedItems.fhirNonSuccess,
                groupedItems.pluginNonSuccess,
                pluginResult,
                pluginNameShort
        );

        // Step 8: Build final validation result
        DsfLinter.PluginLinter pluginLinter = buildPluginLintResult(
                pluginName,
                plugin,
                itemsCollection,
                pluginResult,
                metadataItems,
                leftoverItems
        );

        // Step 9: Print plugin summary
        reportGenerator.printPluginSummary(pluginLinter.output());

        return pluginLinter;
    }

    /**
     * Collects Lint Items from BPMN and FHIR validators.
     * Includes resource root Lint Items, dependency items, and individual success items.
     */
    private LintingItemsCollection collectLintingItems(
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin)
            throws ResourceLinterException {

        // Call ENHANCED validate methods with outsideRoot maps, dependency maps, and resource root
        LintingResult bpmnResult = bpmnValidator.lint(
                pluginName,
                plugin.bpmnFiles(),
                plugin.missingBpmnRefs(),
                plugin.bpmnOutsideRoot(),
                plugin.bpmnFromDependencies(),
                plugin.pluginSpecificResourceRoot()
        );

        LintingResult fhirResult = fhirValidator.lint(
                pluginName,
                plugin.fhirFiles(),
                plugin.missingFhirRefs(),
                plugin.fhirOutsideRoot(),
                plugin.fhirFromDependencies(),
                plugin.pluginSpecificResourceRoot()
        );

        List<AbstractLintItem> allLintingItems = new ArrayList<>(bpmnResult.getItems());
        allLintingItems.addAll(fhirResult.getItems());

        List<AbstractLintItem> pluginLevelItems = allLintingItems.stream()
                .filter(i -> i instanceof PluginLintItem)
                .toList();

        List<AbstractLintItem> nonPluginItems = allLintingItems.stream()
                .filter(i -> !(i instanceof PluginLintItem))
                .toList();

        return new LintingItemsCollection(nonPluginItems, pluginLevelItems);
    }

    /**
     * Groups and filters Lint Items for console display.
     */
    private GroupedLintingItems groupAndFilterItems(
            LintingItemsCollection itemsCollection,
            LintingResult pluginResult,
            List<AbstractLintItem> metadataItems,
            List<AbstractLintItem> leftoverItems) {

        List<AbstractLintItem> bpmnNonSuccess = LintingUtils.onlyBpmnItems(
                        itemsCollection.nonPluginItems
                ).stream()
                .filter(i -> i.getSeverity() != LinterSeverity.SUCCESS)
                .toList();

        List<AbstractLintItem> fhirNonSuccess = LintingUtils.onlyFhirItems(
                        itemsCollection.nonPluginItems
                ).stream()
                .filter(i -> i.getSeverity() != LinterSeverity.SUCCESS)
                .toList();

        List<AbstractLintItem> allPluginItems = new ArrayList<>(pluginResult.getItems());

        // Add metadata Lint Items
        if (metadataItems != null && !metadataItems.isEmpty()) {
            allPluginItems.addAll(metadataItems);
        }

        if (leftoverItems != null && !leftoverItems.isEmpty()) {
            allPluginItems.addAll(leftoverItems);
        }

        List<AbstractLintItem> pluginNonSuccess = LintingUtils.onlyPluginItems(
                        allPluginItems
                ).stream()
                .filter(i -> i.getSeverity() != LinterSeverity.SUCCESS)
                .toList();

        return new GroupedLintingItems(bpmnNonSuccess, fhirNonSuccess, pluginNonSuccess);
    }

    /**
     * Builds the final PluginLinter result for a single plugin.
     */
    private DsfLinter.PluginLinter buildPluginLintResult(
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin,
            LintingItemsCollection itemsCollection,
            LintingResult pluginResult,
            List<AbstractLintItem> metadataItems,
            List<AbstractLintItem> leftoverItems) throws IOException {

        List<AbstractLintItem> finalLintingItems = new ArrayList<>();
        finalLintingItems.addAll(itemsCollection.nonPluginItems);
        finalLintingItems.addAll(pluginResult.getItems());

        // Add metadata Lint Items to final result
        if (metadataItems != null && !metadataItems.isEmpty()) {
            finalLintingItems.addAll(metadataItems);
        }

        if (leftoverItems != null && !leftoverItems.isEmpty()) {
            finalLintingItems.addAll(leftoverItems);
        }

        LintingOutput finalOutput = new LintingOutput(finalLintingItems);

        Path pluginReportPath = reportBasePath.resolve(pluginName);
        Files.createDirectories(pluginReportPath);

        return new DsfLinter.PluginLinter(
                pluginName,
                plugin.adapter().sourceClass().getName(),
                plugin.apiVersion(),
                finalOutput,
                pluginReportPath
        );
    }

    /**
     * Internal record to hold separated Lint Items.
     */
    private record LintingItemsCollection(
            List<AbstractLintItem> nonPluginItems,
            List<AbstractLintItem> pluginLevelItems
    ) {}

    /**
     * Internal record to hold grouped non-SUCCESS Lint Items for display.
     */
    private record GroupedLintingItems(
            List<AbstractLintItem> bpmnNonSuccess,
            List<AbstractLintItem> fhirNonSuccess,
            List<AbstractLintItem> pluginNonSuccess
    ) {}
}