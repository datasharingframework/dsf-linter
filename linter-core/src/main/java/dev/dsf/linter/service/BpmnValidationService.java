package dev.dsf.linter.service;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.bpmn.BPMNValidator;
import dev.dsf.linter.exception.ResourceValidationException;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.validation.ValidationOutput;
import dev.dsf.linter.util.resource.ResourceResolver;
import dev.dsf.linter.output.ValidationSeverity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for validating BPMN resources.
 * Enhanced with resource root validation and dependency JAR support.
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class BpmnValidationService {

    private final Logger logger;
    private final BPMNValidator bpmnValidator;

    public BpmnValidationService(Logger logger) {
        this.logger = logger;
        this.bpmnValidator = new BPMNValidator();
    }

    /**
     * Validates all BPMN aspects for a specific plugin.
     * Enhanced to include validation of resource locations.
     *
     * @param pluginName       The name of the plugin being validated
     * @param bpmnFiles        List of existing BPMN files to validate
     * @param missingBpmnRefs  List of BPMN file paths that were referenced but not found
     * @return ValidationResult containing all collected validation items
     * @throws ResourceValidationException if a severe error occurs during validation
     */
    public ValidationResult validate(String pluginName, List<File> bpmnFiles, List<String> missingBpmnRefs)
            throws ResourceValidationException {
        List<AbstractValidationItem> allItems = new ArrayList<>();

        // Process existing files first
        allItems.addAll(validateExistingFiles(pluginName, bpmnFiles));

        // Then add missing reference errors
        allItems.addAll(createMissingReferenceItems(pluginName, missingBpmnRefs));

        return new ValidationResult(allItems);
    }

    /**
     * Enhanced validation method that includes resource root validation and dependency JAR support.
     * This is the new method that should be called from orchestrator.
     *
     * @param pluginName          The name of the plugin being validated
     * @param bpmnFiles           List of existing BPMN files to validate
     * @param missingBpmnRefs     List of BPMN file paths that were referenced but not found
     * @param bpmnOutsideRoot     Map of BPMN files found outside expected resource root
     * @param bpmnFromDependencies Map of BPMN files found in dependency JARs
     * @param pluginResourceRoot  The plugin-specific resource root for success items
     * @return ValidationResult containing all collected validation items
     * @throws ResourceValidationException if a severe error occurs during validation
     */
    public ValidationResult validate(
            String pluginName,
            List<File> bpmnFiles,
            List<String> missingBpmnRefs,
            Map<String, ResourceResolver.ResolutionResult> bpmnOutsideRoot,
            Map<String, ResourceResolver.ResolutionResult> bpmnFromDependencies,
            File pluginResourceRoot)
            throws ResourceValidationException {

        List<AbstractValidationItem> allItems = new ArrayList<>();

        allItems.addAll(validateExistingFiles(pluginName, bpmnFiles));
        allItems.addAll(createMissingReferenceItems(pluginName, missingBpmnRefs));
        allItems.addAll(createOutsideRootItems(pluginName, bpmnOutsideRoot));
        allItems.addAll(createDependencyItems(pluginName, bpmnFromDependencies));
        allItems.addAll(createSuccessItemsForValidResources(pluginName, bpmnFiles, pluginResourceRoot));

        return new ValidationResult(allItems);
    }

    /**
     * Creates validation items for BPMN files found outside expected resource root.
     *
     * @param pluginName the plugin name
     * @param bpmnOutsideRoot map of files found outside root
     * @return list of validation items
     */
    private List<AbstractValidationItem> createOutsideRootItems(
            String pluginName,
            Map<String, ResourceResolver.ResolutionResult> bpmnOutsideRoot) {

        List<AbstractValidationItem> items = new ArrayList<>();

        if (bpmnOutsideRoot == null || bpmnOutsideRoot.isEmpty()) {
            return items;
        }

        for (Map.Entry<String, ResourceResolver.ResolutionResult> entry : bpmnOutsideRoot.entrySet()) {
            String reference = entry.getKey();
            ResourceResolver.ResolutionResult result = entry.getValue();

            if (result.file().isPresent()) {
                items.add(new PluginDefinitionBpmnFileReferencedFoundOutsideExpectedRootValidationItem(
                        pluginName,
                        result.file().get(),
                        reference,
                        result.expectedRoot(),
                        result.actualLocation()
                ));
            }
        }

        return items;
    }

    /**
     * Creates INFO validation items for BPMN files found in dependency JARs.
     * These are treated as SUCCESS since dependencies are expected to provide resources.
     *
     * @param pluginName the plugin name
     * @param bpmnFromDependencies map of files found in dependencies
     * @return list of INFO validation items
     */
    private List<AbstractValidationItem> createDependencyItems(
            String pluginName,
            Map<String, ResourceResolver.ResolutionResult> bpmnFromDependencies) {

        return dev.dsf.linter.util.validation.ValidationUtils.createDependencySuccessItems(
                pluginName,
                bpmnFromDependencies,
                "BPMN"
        );
    }

    /**
     * Creates validation items for missing BPMN references.
     *
     * @param missingBpmnRefs list of missing BPMN file references
     * @return list of validation items for missing references
     */
    private List<AbstractValidationItem> createMissingReferenceItems(String pluginName, List<String> missingBpmnRefs) {
        List<AbstractValidationItem> items = new ArrayList<>();

        for (String missing : missingBpmnRefs) {
            PluginDefinitionBpmnFileReferencedButNotFoundValidationItem notFoundItem =
                    new PluginDefinitionBpmnFileReferencedButNotFoundValidationItem(
                            pluginName,
                            ValidationSeverity.ERROR,
                            new File(missing),
                            "Referenced BPMN file not found"
                    );
            items.add(notFoundItem);
        }
        return items;
    }

    /**
     * Validates all existing BPMN files.
     *
     * @param bpmnFiles list of BPMN files to validate
     * @return list of all validation items from file validation
     */
    private List<AbstractValidationItem> validateExistingFiles(String pluginName, List<File> bpmnFiles) {
        List<AbstractValidationItem> allItems = new ArrayList<>();

        for (File bpmnFile : bpmnFiles) {
            List<AbstractValidationItem> fileItems = validateSingleBpmnFile(pluginName, bpmnFile);
            allItems.addAll(fileItems);
        }

        return allItems;
    }

    /**
     * Validates a single BPMN file.
     */
    private List<AbstractValidationItem> validateSingleBpmnFile(String pluginName, File bpmnFile) {

        logger.info("Validating BPMN file: " + bpmnFile.getName());

        // Validate the file using BPMNValidator
        ValidationOutput output = bpmnValidator.validateBpmnFile(bpmnFile.toPath());
        List<AbstractValidationItem> itemsForThisFile = new ArrayList<>(output.validationItems());

        // Check if file was successfully parsed (no Unparsable items)
        boolean hasUnparsableItem = itemsForThisFile.stream()
                .anyMatch(item -> item instanceof PluginDefinitionUnparsableBpmnResourceValidationItem);

        if (!hasUnparsableItem) {
            // Add PluginDefinitionValidationItemSuccess for successful parsing
            PluginDefinitionValidationItemSuccess pluginSuccessItem = new PluginDefinitionValidationItemSuccess(
                    bpmnFile,
                    pluginName,
                    String.format("BPMN file '%s' successfully parsed and validated for plugin '%s'",
                            bpmnFile.getName(), pluginName)
            );
            itemsForThisFile.add(pluginSuccessItem);
        }

        // Extract process ID and add BPMN-specific success item
        String processId = extractProcessId(output);
        itemsForThisFile.add(createBpmnSuccessItem(bpmnFile, processId));

        return itemsForThisFile;
    }

    /**
     * Extracts and normalizes the process ID from validation output.
     *
     * @param output the validation output
     * @return normalized process ID or "[unknown_process]" if blank
     */
    private String extractProcessId(ValidationOutput output) {
        String processId = output.getProcessId();
        return processId.isBlank() ? "[unknown_process]" : processId;
    }

    /**
     * Creates BPMN-specific success item.
     */
    private BpmnElementValidationItemSuccess createBpmnSuccessItem(File bpmnFile, String processId) {
        return new BpmnElementValidationItemSuccess(
                processId,
                bpmnFile,
                processId,
                "Referenced BPMN file found and is readable."
        );
    }

    /**
     * Creates success items for BPMN resources that are correctly located in resource root.
     * One success item per valid resource.
     *
     * @param pluginName the plugin name
     * @param bpmnFiles list of valid BPMN files
     * @param pluginResourceRoot the plugin's resource root
     * @return list of success validation items
     */
    private List<AbstractValidationItem> createSuccessItemsForValidResources(
            String pluginName,
            List<File> bpmnFiles,
            File pluginResourceRoot) {

        List<AbstractValidationItem> items = new ArrayList<>();

        for (File bpmnFile : bpmnFiles) {
            items.add(new PluginDefinitionValidationItemSuccess(
                    bpmnFile,
                    pluginName,
                    String.format("BPMN resource '%s' correctly located in resource root",
                            bpmnFile.getName())
            ));
        }

        return items;
    }
}