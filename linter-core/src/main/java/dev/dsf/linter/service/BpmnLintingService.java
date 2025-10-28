package dev.dsf.linter.service;

import dev.dsf.linter.bpmn.BpmnLinter;
import dev.dsf.linter.exception.ResourceLinterException;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.util.linting.LintingUtils;
import dev.dsf.linter.util.resource.ResourceResolver;
import dev.dsf.linter.output.LinterSeverity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for linting BPMN resources.
 * Enhanced with resource root linting and dependency JAR support.
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class BpmnLintingService {

    private final Logger logger;
    private final BpmnLinter bpmnLinter;

    public BpmnLintingService(Logger logger) {
        this.logger = logger;
        this.bpmnLinter = new BpmnLinter();
    }

    /**
     * lints all BPMN aspects for a specific plugin.
     * Enhanced to include linting of resource locations.
     *
     * @param pluginName       The name of the plugin being linted
     * @param bpmnFiles        List of existing BPMN files to lint
     * @param missingBpmnRefs  List of BPMN file paths that were referenced but not found
     * @return LintingResult containing all collected lint items
     * @throws ResourceLinterException if a severe error occurs during linting
     */
    public LintingResult lint(String pluginName, List<File> bpmnFiles, List<String> missingBpmnRefs)
            throws ResourceLinterException {
        List<AbstractLintItem> allItems = new ArrayList<>();

        // Process existing files first
        allItems.addAll(lintExistingFiles(pluginName, bpmnFiles));

        // Then add missing reference errors
        allItems.addAll(createMissingReferenceItems(pluginName, missingBpmnRefs));

        return new LintingResult(allItems);
    }

    /**
     * Enhanced lint method that includes resource root linting and dependency JAR support.
     * This is the new method that should be called from orchestrator.
     *
     * @param pluginName          The name of the plugin being linted
     * @param bpmnFiles           List of existing BPMN files to lint
     * @param missingBpmnRefs     List of BPMN file paths that were referenced but not found
     * @param bpmnOutsideRoot     Map of BPMN files found outside expected resource root
     * @param bpmnFromDependencies Map of BPMN files found in dependency JARs
     * @param pluginResourceRoot  The plugin-specific resource root for success items
     * @return LintingResult containing all collected lint items
     * @throws ResourceLinterException if a severe error occurs during linting
     */
    public LintingResult lint(
            String pluginName,
            List<File> bpmnFiles,
            List<String> missingBpmnRefs,
            Map<String, ResourceResolver.ResolutionResult> bpmnOutsideRoot,
            Map<String, ResourceResolver.ResolutionResult> bpmnFromDependencies,
            File pluginResourceRoot)
            throws ResourceLinterException {

        List<AbstractLintItem> allItems = new ArrayList<>();

        allItems.addAll(lintExistingFiles(pluginName, bpmnFiles));
        allItems.addAll(createMissingReferenceItems(pluginName, missingBpmnRefs));
        allItems.addAll(createOutsideRootItems(pluginName, bpmnOutsideRoot));
        allItems.addAll(createDependencyItems(pluginName, bpmnFromDependencies));
        allItems.addAll(createSuccessItemsForValidResources(pluginName, bpmnFiles, pluginResourceRoot));

        return new LintingResult(allItems);
    }

    /**
     * Creates lint items for BPMN files found outside expected resource root.
     *
     * @param pluginName the plugin name
     * @param bpmnOutsideRoot map of files found outside root
     * @return list of lint items
     */
    private List<AbstractLintItem> createOutsideRootItems(
            String pluginName,
            Map<String, ResourceResolver.ResolutionResult> bpmnOutsideRoot) {

        List<AbstractLintItem> items = new ArrayList<>();

        if (bpmnOutsideRoot == null || bpmnOutsideRoot.isEmpty()) {
            return items;
        }

        for (Map.Entry<String, ResourceResolver.ResolutionResult> entry : bpmnOutsideRoot.entrySet()) {
            String reference = entry.getKey();
            ResourceResolver.ResolutionResult result = entry.getValue();

            if (result.file().isPresent()) {
                items.add(new PluginDefinitionBpmnFileReferencedFoundOutsideExpectedRootLintItem(
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
     * Creates INFO lint items for BPMN files found in dependency JARs.
     * These are treated as SUCCESS since dependencies are expected to provide resources.
     *
     * @param pluginName the plugin name
     * @param bpmnFromDependencies map of files found in dependencies
     * @return list of INFO lint items
     */
    private List<AbstractLintItem> createDependencyItems(
            String pluginName,
            Map<String, ResourceResolver.ResolutionResult> bpmnFromDependencies) {

        return LintingUtils.createDependencySuccessItems(
                pluginName,
                bpmnFromDependencies,
                "BPMN"
        );
    }

    /**
     * Creates lint items for missing BPMN references.
     *
     * @param missingBpmnRefs list of missing BPMN file references
     * @return list of lint items for missing references
     */
    private List<AbstractLintItem> createMissingReferenceItems(String pluginName, List<String> missingBpmnRefs) {
        List<AbstractLintItem> items = new ArrayList<>();

        for (String missing : missingBpmnRefs) {
            PluginDefinitionBpmnFileReferencedButNotFoundLintItem notFoundItem =
                    new PluginDefinitionBpmnFileReferencedButNotFoundLintItem(
                            pluginName,
                            LinterSeverity.ERROR,
                            new File(missing),
                            "Referenced BPMN file not found"
                    );
            items.add(notFoundItem);
        }
        return items;
    }

    /**
     * lints all existing BPMN files.
     *
     * @param bpmnFiles list of BPMN files to lint
     * @return list of all lint items from file linting
     */
    private List<AbstractLintItem> lintExistingFiles(String pluginName, List<File> bpmnFiles) {
        List<AbstractLintItem> allItems = new ArrayList<>();

        for (File bpmnFile : bpmnFiles) {
            List<AbstractLintItem> fileItems = lintSingleBpmnFile(pluginName, bpmnFile);
            allItems.addAll(fileItems);
        }

        return allItems;
    }

    /**
     * lints a single BPMN file.
     */
    private List<AbstractLintItem> lintSingleBpmnFile(String pluginName, File bpmnFile) {

        logger.info("Linting BPMN file: " + bpmnFile.getName());

        // lint the file using BpmnLinter
        LintingOutput output = bpmnLinter.lintBpmnFile(bpmnFile.toPath());
        List<AbstractLintItem> itemsForThisFile = new ArrayList<>(output.LintItems());

        // Check if file was successfully parsed (no Unparsable items)
        boolean hasUnparsableItem = itemsForThisFile.stream()
                .anyMatch(item -> item instanceof PluginDefinitionUnparsableBpmnResourceLintItem);

        if (!hasUnparsableItem) {
            // Add PluginDefinitionLintItemSuccess for successful parsing
            PluginDefinitionLintItemSuccess pluginSuccessItem = new PluginDefinitionLintItemSuccess(
                    bpmnFile,
                    pluginName,
                    String.format("BPMN file '%s' successfully parsed and linted for plugin '%s'",
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
     * Extracts and normalizes the process ID from linting output.
     *
     * @param output the linting output
     * @return normalized process ID or "[unknown_process]" if blank
     */
    private String extractProcessId(LintingOutput output) {
        String processId = output.getProcessId();
        return processId.isBlank() ? "[unknown_process]" : processId;
    }

    /**
     * Creates BPMN-specific success item.
     */
    private BpmnElementLintItemSuccess createBpmnSuccessItem(File bpmnFile, String processId) {
        return new BpmnElementLintItemSuccess(
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
     * @return list of success lint items
     */
    private List<AbstractLintItem> createSuccessItemsForValidResources(
            String pluginName,
            List<File> bpmnFiles,
            File pluginResourceRoot) {

        List<AbstractLintItem> items = new ArrayList<>();

        for (File bpmnFile : bpmnFiles) {
            items.add(new PluginDefinitionLintItemSuccess(
                    bpmnFile,
                    pluginName,
                    String.format("BPMN resource '%s' correctly located in resource root",
                            bpmnFile.getName())
            ));
        }

        return items;
    }
}