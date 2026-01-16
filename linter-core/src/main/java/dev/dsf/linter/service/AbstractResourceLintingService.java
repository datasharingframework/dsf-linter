package dev.dsf.linter.service;

import dev.dsf.linter.exception.ResourceLinterException;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.output.item.PluginLintItem;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.util.linting.LintingUtils;
import dev.dsf.linter.util.resource.ResourceResolutionResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for resource linting services.
 * <p>
 * This class eliminates code duplication between BpmnLintingService and FhirLintingService
 * by providing a generic implementation of common linting operations.
 * </p>
 *
 * <h3>Design Pattern:</h3>
 * <p>Template Method Pattern - defines the skeleton of linting algorithms,
 * allowing subclasses to override specific steps without changing the algorithm structure.</p>
 *
 * @since 1.1.0
 */
public abstract class AbstractResourceLintingService {

    protected final Logger logger;

    protected AbstractResourceLintingService(Logger logger) {
        this.logger = logger;
    }

    // PUBLIC API - Template Methods

    /**
     * Main linting method with full resource root validation and dependency tracking.
     * <p>
     * This is the template method that orchestrates the entire linting process.
     * </p>
     *
     * @param pluginName          the name of the plugin being linted
     * @param resourceFiles       list of resource files to lint
     * @param missingRefs         list of missing resource references
     * @param outsideRoot         map of files found outside expected resource root
     * @param fromDependencies    map of files found in dependency JARs
     * @param pluginResourceRoot  the plugin-specific resource root
     * @return LintingResult containing all lint items
     * @throws ResourceLinterException if linting fails
     */
    public LintingResult lint(
            String pluginName,
            List<File> resourceFiles,
            List<String> missingRefs,
            Map<String, ResourceResolutionResult> outsideRoot,
            Map<String, ResourceResolutionResult> fromDependencies,
            File pluginResourceRoot)
            throws ResourceLinterException {

        List<AbstractLintItem> allItems = new ArrayList<>();

        // Step 1: lint missing references
        allItems.addAll(createMissingReferenceItems(pluginName, missingRefs));

        // Step 2: lint existing files
        allItems.addAll(lintExistingFiles(pluginName, resourceFiles));

        // Step 3: Create items for files outside root
        allItems.addAll(createOutsideRootItems(pluginName, outsideRoot));

        // Step 4: Create success items for dependency resources
        allItems.addAll(createDependencyItems(pluginName, fromDependencies));

        // Step 5: Create success items for valid resources
        allItems.addAll(createSuccessItemsForValidResources(pluginName, resourceFiles, pluginResourceRoot));

        return new LintingResult(allItems);
    }

    // PROTECTED TEMPLATE METHODS - To be implemented by subclasses

    /**
     * Returns the name of the resource type (e.g., "BPMN", "FHIR").
     *
     * @return resource type name for logging
     */
    protected abstract String getResourceTypeName();

    /**
     * lints a single resource file and returns lint items.
     *
     * @param pluginName the plugin name
     * @param resourceFile the file to lint
     * @return linting output containing lint items
     */
    protected abstract LintingOutput lintSingleFile(String pluginName, File resourceFile);

    /**
     * Creates a lint item for a missing resource reference.
     *
     * @param pluginName the plugin name
     * @param missingRef the missing reference
     * @return lint item representing the missing reference
     */
    protected abstract AbstractLintItem createMissingReferenceLintItem(String pluginName, String missingRef);

    /**
     * Creates a lint item for a resource found outside expected root.
     *
     * @param pluginName the plugin name
     * @param reference the resource reference
     * @param result the resolution result
     * @return lint item representing the outside-root violation
     */
    protected abstract AbstractLintItem createOutsideRootLintItem(
            String pluginName,
            String reference,
            ResourceResolutionResult result);

    /**
     * Creates a success lint item for a resource file.
     *
     * @param pluginName the plugin name
     * @param resourceFile the resource file
     * @param output the linting output
     * @return success lint item
     */
    protected abstract AbstractLintItem createResourceSuccessItem(
            String pluginName,
            File resourceFile,
            LintingOutput output);

    /**
     * Creates a success lint item for plugin-level validation.
     *
     * @param pluginName the plugin name
     * @param resourceFile the resource file
     * @return plugin-level success item
     */
    protected abstract AbstractLintItem createPluginSuccessItem(String pluginName, File resourceFile);

    /**
     * Checks if a lint item represents an unparsable resource.
     *
     * @param item the lint item to check
     * @return true if the item indicates parsing failure
     */
    protected abstract boolean isUnparsableItem(AbstractLintItem item);

    // PRIVATE HELPER METHODS - Common implementation for all subclasses

    /**
     * Creates lint items for missing resource references.
     */
    private List<AbstractLintItem> createMissingReferenceItems(String pluginName, List<String> missingRefs) {
        List<AbstractLintItem> items = new ArrayList<>();

        for (String missing : missingRefs) {
            items.add(createMissingReferenceLintItem(pluginName, missing));
        }

        return items;
    }

    /**
     * Creates lint items for resources found outside expected root.
     */
    private List<AbstractLintItem> createOutsideRootItems(
            String pluginName,
            Map<String, ResourceResolutionResult> outsideRoot) {

        List<AbstractLintItem> items = new ArrayList<>();

        if (outsideRoot == null || outsideRoot.isEmpty()) {
            return items;
        }

        for (Map.Entry<String, ResourceResolutionResult> entry : outsideRoot.entrySet()) {
            String reference = entry.getKey();
            ResourceResolutionResult result = entry.getValue();

            if (result.file().isPresent()) {
                items.add(createOutsideRootLintItem(pluginName, reference, result));
            }
        }

        return items;
    }

    /**
     * Creates INFO lint items for resources found in dependency JARs.
     */
    private List<AbstractLintItem> createDependencyItems(
            String pluginName,
            Map<String, ResourceResolutionResult> fromDependencies) {

        return LintingUtils.createDependencySuccessItems(
                pluginName,
                fromDependencies,
                getResourceTypeName()
        );
    }

    /**
     * lints all existing resource files.
     */
    private List<AbstractLintItem> lintExistingFiles(String pluginName, List<File> resourceFiles) {
        List<AbstractLintItem> allItems = new ArrayList<>();

        for (File resourceFile : resourceFiles) {
            List<AbstractLintItem> fileItems = lintSingleResourceFile(pluginName, resourceFile);
            allItems.addAll(fileItems);
        }

        return allItems;
    }

    /**
     * lints a single resource file with common structure.
     */
    private List<AbstractLintItem> lintSingleResourceFile(String pluginName, File resourceFile) {

        logger.info("Linting " + getResourceTypeName() + " file: " + resourceFile.getName());

        LintingOutput output = lintSingleFile(pluginName, resourceFile);
        List<AbstractLintItem> itemsForThisFile = new ArrayList<>(output.LintItems());

        boolean hasUnparsableItem = itemsForThisFile.stream()
                .anyMatch(this::isUnparsableItem);

        if (!hasUnparsableItem) {
            itemsForThisFile.add(createPluginSuccessItem(pluginName, resourceFile));
        }

        itemsForThisFile.add(createResourceSuccessItem(pluginName, resourceFile, output));

        return itemsForThisFile;
    }

    /**
     * Creates success items for resources correctly located in root.
     */
    private List<AbstractLintItem> createSuccessItemsForValidResources(
            String pluginName,
            List<File> resourceFiles,
            File pluginResourceRoot) {

        List<AbstractLintItem> items = new ArrayList<>();

        for (File resourceFile : resourceFiles) {
            items.add(PluginLintItem.success(
                    resourceFile,
                    pluginName,
                    String.format("%s resource '%s' correctly located in resource root",
                            getResourceTypeName(),
                            resourceFile.getName())
            ));
        }

        return items;
    }
}
