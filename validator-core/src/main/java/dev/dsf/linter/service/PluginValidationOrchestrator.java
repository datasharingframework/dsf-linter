package dev.dsf.linter.service;

import dev.dsf.linter.DsfValidatorImpl;
import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.analysis.LeftoverResourceDetector;
import dev.dsf.linter.exception.MissingServiceRegistrationException;
import dev.dsf.linter.exception.ResourceValidationException;
import dev.dsf.linter.item.AbstractValidationItem;
import dev.dsf.linter.item.PluginValidationItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.report.ValidationReportGenerator;
import dev.dsf.linter.setup.ProjectSetupHandler;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.validation.ValidationOutput;
import dev.dsf.linter.util.validation.ValidationUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the complete validation process for a single plugin.
 * This class encapsulates all the complex logic required to validate one plugin,
 * including collecting validation items from BPMN and FHIR validators, running
 * plugin-level validation, handling leftover resources, and coordinating console output.
 */
public class PluginValidationOrchestrator {

    private final BpmnValidationService bpmnValidator;
    private final FhirValidationService fhirValidator;
    private final PluginValidationService pluginValidator;
    private final LeftoverResourceDetector leftoverDetector;
    private final ValidationReportGenerator reportGenerator;
    private final Path reportBasePath;

    /**
     * Context information for validating a plugin in a multi-plugin environment.
     */
    public record PluginValidationContext(
            int pluginIndex,
            int totalPlugins,
            boolean isLastPlugin,
            boolean isSinglePluginProject
    ) {}

    /**
     * Constructs a new orchestrator with required dependencies.
     */
    public PluginValidationOrchestrator(
            BpmnValidationService bpmnValidator,
            FhirValidationService fhirValidator,
            PluginValidationService pluginValidator,
            LeftoverResourceDetector leftoverDetector,
            ValidationReportGenerator reportGenerator,
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
     * Validates a single plugin completely.
     * This method orchestrates all validation steps:
     * 1. Sets API version context
     * 2. Prints plugin header
     * 3. Collects BPMN and FHIR validation items
     * 4. Runs plugin-level validation
     * 5. Assigns leftover resource items
     * 6. Groups and filters items for display
     * 7. Prints validation sections
     * 8. Builds final result
     * 9. Prints plugin summary
     *
     * @param pluginName The unique name of the plugin
     * @param plugin The plugin discovery information
     * @param context The project context
     * @param leftoverAnalysis The project-wide leftover analysis result
     * @param validationContext Context about the plugin's position in the validation sequence
     * @return Complete validation result for this plugin
     */
    public DsfValidatorImpl.PluginValidation validateSinglePlugin(
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin,
            ProjectSetupHandler.ProjectContext context,
            LeftoverResourceDetector.AnalysisResult leftoverAnalysis,
            PluginValidationContext validationContext)
            throws ResourceValidationException, IOException, MissingServiceRegistrationException {

        // Step 1: Set API version for downstream validators
        ApiVersionHolder.setVersion(plugin.apiVersion());

        // Step 2: Print plugin header
        reportGenerator.printPluginHeader(
                pluginName,
                validationContext.pluginIndex(),
                validationContext.totalPlugins()
        );

        // Step 3: Collect BPMN and FHIR validation items
        ValidationItemsCollection itemsCollection = collectValidationItems(pluginName, plugin);

        // Step 4: Run plugin-level validation
        ValidationResult pluginResult = pluginValidator.validatePlugin(
                context.projectPath(),
                plugin.adapter(),
                plugin.apiVersion(),
                itemsCollection.pluginLevelItems
        );

        // Step 5: Get leftover items for this plugin
        List<AbstractValidationItem> leftoverItems = leftoverDetector.getItemsForPlugin(
                leftoverAnalysis,
                pluginName,
                plugin,
                validationContext.isLastPlugin(),
                validationContext.isSinglePluginProject()
        );

        // Step 6: Group and filter items for console display
        GroupedValidationItems groupedItems = groupAndFilterItems(
                itemsCollection,
                pluginResult,
                leftoverItems
        );

        // Step 7: Print validation sections (BPMN → FHIR → Plugin)
        String pluginNameShort = plugin.adapter().sourceClass().getSimpleName();
        reportGenerator.printValidationSections(
                groupedItems.bpmnNonSuccess,
                groupedItems.fhirNonSuccess,
                groupedItems.pluginNonSuccess,
                pluginResult,
                pluginNameShort
        );

        // Step 8: Build final validation result
        DsfValidatorImpl.PluginValidation pluginValidation = buildPluginValidationResult(
                pluginName,
                plugin,
                itemsCollection,
                pluginResult,
                leftoverItems
        );

        // Step 9: Print plugin summary
        reportGenerator.printPluginSummary(pluginValidation.output());

        return pluginValidation;
    }

    /**
     * Collects validation items from BPMN and FHIR validators.
     * Separates plugin-level items from pure BPMN/FHIR items.
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
     * Groups and filters validation items for console display.
     * Returns only non-SUCCESS items for each category (BPMN, FHIR, Plugin).
     */
    private GroupedValidationItems groupAndFilterItems(
            ValidationItemsCollection itemsCollection,
            ValidationResult pluginResult,
            List<AbstractValidationItem> leftoverItems) {

        List<AbstractValidationItem> bpmnNonSuccess = ValidationUtils.onlyBpmnItems(
                        itemsCollection.nonPluginItems
                ).stream()
                .filter(i -> i.getSeverity() != ValidationSeverity.SUCCESS)
                .toList();

        List<AbstractValidationItem> fhirNonSuccess = ValidationUtils.onlyFhirItems(
                        itemsCollection.nonPluginItems
                ).stream()
                .filter(i -> i.getSeverity() != ValidationSeverity.SUCCESS)
                .toList();

        List<AbstractValidationItem> allPluginItems = new ArrayList<>(pluginResult.getItems());
        if (leftoverItems != null && !leftoverItems.isEmpty()) {
            allPluginItems.addAll(leftoverItems);
        }

        List<AbstractValidationItem> pluginNonSuccess = ValidationUtils.onlyPluginItems(
                        allPluginItems
                ).stream()
                .filter(i -> i.getSeverity() != ValidationSeverity.SUCCESS)
                .toList();

        return new GroupedValidationItems(bpmnNonSuccess, fhirNonSuccess, pluginNonSuccess);
    }

    /**
     * Builds the final PluginValidation result for a single plugin.
     */
    private DsfValidatorImpl.PluginValidation buildPluginValidationResult(
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

        Path pluginReportPath = reportBasePath.resolve(pluginName);
        Files.createDirectories(pluginReportPath);

        return new DsfValidatorImpl.PluginValidation(
                pluginName,
                plugin.adapter().sourceClass().getName(),
                plugin.apiVersion(),
                finalOutput,
                pluginReportPath
        );
    }

    /**
     * Internal record to hold separated validation items.
     */
    private record ValidationItemsCollection(
            List<AbstractValidationItem> nonPluginItems,
            List<AbstractValidationItem> pluginLevelItems
    ) {}

    /**
     * Internal record to hold grouped non-SUCCESS validation items for display.
     */
    private record GroupedValidationItems(
            List<AbstractValidationItem> bpmnNonSuccess,
            List<AbstractValidationItem> fhirNonSuccess,
            List<AbstractValidationItem> pluginNonSuccess
    ) {}
}