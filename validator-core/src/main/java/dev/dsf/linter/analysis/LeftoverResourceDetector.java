package dev.dsf.linter.analysis;

import dev.dsf.linter.item.AbstractValidationItem;
import dev.dsf.linter.item.PluginDefinitionProcessPluginRessourceNotLoadedValidationItem;
import dev.dsf.linter.item.PluginDefinitionValidationItemSuccess;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.service.ResourceDiscoveryService;
import dev.dsf.linter.util.resource.FhirFileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for detecting unreferenced resources in a project.
 *
 * <p>This service identifies:
 * <ul>
 *   <li>BPMN files that exist on disk but are not referenced in the plugin definition</li>
 *   <li>FHIR files that exist on disk but are not referenced in the plugin definition</li>
 *   <li>Leftover or orphaned resource files</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class LeftoverResourceDetector {

    private final Logger logger;

    /**
     * Constructs a new LeftoverResourceDetector.
     *
     * @param logger the logger for output messages
     */
    public LeftoverResourceDetector(Logger logger) {
        this.logger = logger;
    }

    /**
     * Analyzes the project for unreferenced resources.
     *
     * @param projectDir the project directory
     * @param resourcesDir the resources directory
     * @param referencedBpmnPaths referenced BPMN paths from plugin definition
     * @param referencedFhirPaths referenced FHIR paths from plugin definition
     * @return analysis result containing validation items
     */
    public AnalysisResult analyze(
            File projectDir,
            File resourcesDir,
            Set<String> referencedBpmnPaths,
            Set<String> referencedFhirPaths) {

        logger.info("Analyzing for unreferenced resources...");

        // Find all actual files on disk
        File bpeRoot = new File(resourcesDir, "bpe");
        List<File> actualBpmnFiles = (bpeRoot.exists())
                ? findBpmnFilesRecursively(bpeRoot.toPath())
                : Collections.emptyList();

        File fhirRoot = new File(resourcesDir, "fhir");
        List<File> actualFhirFiles = (fhirRoot.exists())
                ? findFhirFilesRecursively(fhirRoot.toPath())
                : Collections.emptyList();

        // Convert the paths of actual files to be relative to the resources directory
        Set<String> actualBpmnPaths = actualBpmnFiles.stream()
                .map(f -> getRelativePath(f, resourcesDir))
                .collect(Collectors.toSet());

        Set<String> actualFhirPaths = actualFhirFiles.stream()
                .map(f -> getRelativePath(f, resourcesDir))
                .collect(Collectors.toSet());

        // Find the difference: files on disk that are not in the definition
        Set<String> leftoverBpmnPaths = new HashSet<>(actualBpmnPaths);
        leftoverBpmnPaths.removeAll(referencedBpmnPaths);

        Set<String> leftoverFhirPaths = new HashSet<>(actualFhirPaths);
        leftoverFhirPaths.removeAll(referencedFhirPaths);

        // Create validation items
        List<AbstractValidationItem> items = createValidationItems(
                projectDir, resourcesDir, leftoverBpmnPaths, leftoverFhirPaths);

        logAnalysisResults(
                actualBpmnPaths.size(), referencedBpmnPaths.size(), leftoverBpmnPaths.size(),
                actualFhirPaths.size(), referencedFhirPaths.size(), leftoverFhirPaths.size()
        );

        return new AnalysisResult(items, leftoverBpmnPaths, leftoverFhirPaths);
    }

    /**
     * Gets validation items for a specific plugin in a multi-plugin project.
     * This method intelligently assigns leftover items based on plugin context.
     *
     * @param leftoverAnalysis the complete leftover analysis result
     * @param pluginName the name of the current plugin
     * @param plugin the plugin discovery information
     * @param isLastPlugin whether this is the last plugin being processed
     * @param isSinglePluginProject whether this is a single-plugin project
     * @return list of validation items appropriate for this plugin
     */
    public List<AbstractValidationItem> getItemsForPlugin(
            AnalysisResult leftoverAnalysis,
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin,
            boolean isLastPlugin,
            boolean isSinglePluginProject) {

        if (leftoverAnalysis == null || leftoverAnalysis.items().isEmpty()) {
            return Collections.emptyList();
        }

        // Single plugin project: return all items
        if (isSinglePluginProject) {
            logger.debug("Single-plugin project: Returning all leftover items");
            return new ArrayList<>(leftoverAnalysis.items());
        }

        // Multi-plugin project: apply intelligent assignment
        return handleMultiPluginLeftovers(
                leftoverAnalysis.items(),
                pluginName,
                plugin,
                isLastPlugin
        );
    }

    /**
     * Handles leftover items for multi-plugin projects.
     * Strategy:
     * - Assign leftover warnings to the specific plugin if we can determine ownership
     * - Add the success item only to the last plugin if no leftovers exist
     * - For leftovers that can't be assigned, add them to the last plugin
     */
    private List<AbstractValidationItem> handleMultiPluginLeftovers(
            List<AbstractValidationItem> allLeftoverItems,
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin,
            boolean isLastPlugin) {

        List<AbstractValidationItem> result = new ArrayList<>();

        // Check if there are any leftover warning items
        boolean hasLeftoverWarnings = allLeftoverItems.stream()
                .anyMatch(item -> item instanceof PluginDefinitionProcessPluginRessourceNotLoadedValidationItem);

        if (!hasLeftoverWarnings) {
            // Only success items exist - add to last plugin only
            if (isLastPlugin) {
                logger.debug("Multi-plugin project: Adding leftover success item to last plugin");
                result.addAll(allLeftoverItems);
            }
        } else {
            // There are leftover warnings - need to handle them

            // Try to assign leftovers to this specific plugin based on path patterns
            List<AbstractValidationItem> pluginSpecificLeftovers = filterLeftoversForPlugin(
                    allLeftoverItems,
                    pluginName,
                    plugin
            );

            if (!pluginSpecificLeftovers.isEmpty()) {
                logger.debug("Multi-plugin project: Found " + pluginSpecificLeftovers.size() +
                        " plugin-specific leftovers for " + pluginName);
                result.addAll(pluginSpecificLeftovers);
            }

            // Add unassigned leftovers to the last plugin
            if (isLastPlugin) {
                List<AbstractValidationItem> unassignedLeftovers = getUnassignedLeftovers(
                        allLeftoverItems,
                        pluginName
                );

                if (!unassignedLeftovers.isEmpty()) {
                    logger.debug("Multi-plugin project: Adding " + unassignedLeftovers.size() +
                            " unassigned leftovers to last plugin");
                    result.addAll(unassignedLeftovers);
                }
            }
        }

        return result;
    }
    /**
     * Filters leftover items that likely belong to a specific plugin.
     * This is a heuristic based on file paths and plugin naming conventions.
     */
    private List<AbstractValidationItem> filterLeftoversForPlugin(
            List<AbstractValidationItem> allLeftovers,
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin) {

        return allLeftovers.stream()
                .filter(item -> {
                    if (item instanceof PluginDefinitionProcessPluginRessourceNotLoadedValidationItem leftoverItem) {
                        String location = leftoverItem.getLocation();
                        String fileName = leftoverItem.getFileName();

                        // Check if the file path or name contains the plugin name
                        // This is a simple heuristic - you might need to adjust based on your project structure
                        String pluginNameLower = pluginName.toLowerCase();

                        // Additional heuristics could be added here:
                        // - Check against plugin's resource paths
                        // - Use process IDs from plugin's BPMN files
                        // - Match against plugin-specific naming patterns

                        return location.toLowerCase().contains(pluginNameLower) ||
                                fileName.toLowerCase().contains(pluginNameLower);
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Gets leftover items that couldn't be assigned to any specific plugin.
     * These are typically shared resources or resources with unclear ownership.
     */
    private List<AbstractValidationItem> getUnassignedLeftovers(
            List<AbstractValidationItem> allLeftovers,
            String excludePluginName) {

        return allLeftovers.stream()
                .filter(item -> {
                    if (item instanceof PluginDefinitionProcessPluginRessourceNotLoadedValidationItem leftoverItem) {
                        String location = leftoverItem.getLocation();
                        String fileName = leftoverItem.getFileName();

                        // Include items that don't match any plugin name pattern
                        // This is simplified - you might want more sophisticated logic
                        return !location.toLowerCase().contains(excludePluginName.toLowerCase()) &&
                                !fileName.toLowerCase().contains(excludePluginName.toLowerCase());
                    }
                    // Include success items only if they haven't been filtered yet
                    return item instanceof PluginDefinitionValidationItemSuccess;
                })
                .collect(Collectors.toList());
    }

    /**
     * Creates validation items for leftover resources.
     *
     * @param projectDir the project directory
     * @param resourcesDir the resources directory
     * @param leftoverBpmnPaths leftover BPMN paths
     * @param leftoverFhirPaths leftover FHIR paths
     * @return list of validation items
     */
    private List<AbstractValidationItem> createValidationItems(
            File projectDir,
            File resourcesDir,
            Set<String> leftoverBpmnPaths,
            Set<String> leftoverFhirPaths) {

        List<AbstractValidationItem> items = new ArrayList<>();

        if (leftoverBpmnPaths.isEmpty() && leftoverFhirPaths.isEmpty()) {
            // No leftovers found -> success item
            items.add(new PluginDefinitionValidationItemSuccess(
                    projectDir,
                    projectDir.getName(),
                    "All BPMN and FHIR resources found in the project are correctly referenced in ProcessPluginDefinition."
            ));
        } else {
            // Report all leftovers as warnings
            leftoverBpmnPaths.forEach(path -> {
                File file = new File(resourcesDir, path);
                items.add(new PluginDefinitionProcessPluginRessourceNotLoadedValidationItem(
                        file, path,
                        "BPMN file exists but is not referenced in ProcessPluginDefinition"
                ));
            });

            leftoverFhirPaths.forEach(path -> {
                File file = new File(resourcesDir, path);
                items.add(new PluginDefinitionProcessPluginRessourceNotLoadedValidationItem(
                        file, path,
                        "FHIR file exists but is not referenced in ProcessPluginDefinition"
                ));
            });
        }

        return items;
    }

    /**
     * Recursively finds all BPMN files under the given path.
     *
     * @param rootPath the root path to search
     * @return list of BPMN files found
     */
    private List<File> findBpmnFilesRecursively(Path rootPath) {
        return findFiles(rootPath, "glob:**/*.bpmn");
    }

    /**
     * Recursively finds all FHIR files under the given path.
     *
     * @param rootPath the root path to search
     * @return list of FHIR files found
     */
    private List<File> findFhirFilesRecursively(Path rootPath) {
        return findFiles(rootPath, "glob:**/*.{xml,json}").stream()
                .filter(f -> FhirFileUtils.isFhirFile(f.toPath()))
                .collect(Collectors.toList());
    }

    /**
     * Unified file finder using NIO PathMatcher for flexible file filtering.
     *
     * @param root the root directory to search
     * @param glob the glob pattern
     * @return list of matching files
     */
    private List<File> findFiles(Path root, String glob) {
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }

        PathMatcher matcher = root.getFileSystem().getPathMatcher(glob);
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error walking directory " + root + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Converts an absolute file path to a relative path using '/' as separator.
     *
     * @param file the file to convert
     * @param baseDir the base directory
     * @return relative path string
     */
    private String getRelativePath(File file, File baseDir) {
        String relative = baseDir.toPath().relativize(file.toPath()).toString();
        return relative.replace(File.separator, "/");
    }

    /**
     * Logs the analysis results for debugging.
     */
    private void logAnalysisResults(
            int actualBpmn, int referencedBpmn, int leftoverBpmn,
            int actualFhir, int referencedFhir, int leftoverFhir) {

        logger.info("BPMN analysis: " + actualBpmn + " files found, " +
                referencedBpmn + " referenced, " + leftoverBpmn + " unused");

        logger.info("FHIR analysis: " + actualFhir + " files found, " +
                referencedFhir + " referenced, " + leftoverFhir + " unused");

        // Removed logger.warn() calls - leftovers are now only shown in the structured
        // "Unreferenced Resources" section via ProcessPluginRessourceNotLoadedValidationItem
        if (leftoverBpmn > 0 || leftoverFhir > 0) {
            logger.debug("Leftover resources will be displayed in validation output");
        }
    }

    /**
     * Data class containing analysis results.
     */
    public record AnalysisResult(
            List<AbstractValidationItem> items,
            Set<String> leftoverBpmnPaths,
            Set<String> leftoverFhirPaths) {

        public boolean hasLeftovers() {
            return !leftoverBpmnPaths.isEmpty() || !leftoverFhirPaths.isEmpty();
        }

        public int getTotalLeftoverCount() {
            return leftoverBpmnPaths.size() + leftoverFhirPaths.size();
        }
    }
}