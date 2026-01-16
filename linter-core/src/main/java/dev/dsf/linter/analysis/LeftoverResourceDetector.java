package dev.dsf.linter.analysis;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.output.item.PluginLintItem;
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

    public LeftoverResourceDetector(Logger logger) {
        this.logger = logger;
    }

    public AnalysisResult analyze(
            File projectDir,
            File resourcesDir,
            Set<String> referencedBpmnPaths,
            Set<String> referencedFhirPaths) {

        logger.info("Analyzing for unreferenced resources...");

        File bpeRoot = new File(resourcesDir, "bpe");
        List<File> actualBpmnFiles = (bpeRoot.exists())
                ? findBpmnFilesRecursively(bpeRoot.toPath())
                : Collections.emptyList();

        File fhirRoot = new File(resourcesDir, "fhir");
        List<File> actualFhirFiles = (fhirRoot.exists())
                ? findFhirFilesRecursively(fhirRoot.toPath())
                : Collections.emptyList();

        Set<String> actualBpmnPaths = actualBpmnFiles.stream()
                .map(f -> getRelativePath(f, resourcesDir))
                .collect(Collectors.toSet());

        Set<String> actualFhirPaths = actualFhirFiles.stream()
                .map(f -> getRelativePath(f, resourcesDir))
                .collect(Collectors.toSet());

        Set<String> leftoverBpmnPaths = new HashSet<>(actualBpmnPaths);
        leftoverBpmnPaths.removeAll(referencedBpmnPaths);

        Set<String> leftoverFhirPaths = new HashSet<>(actualFhirPaths);
        leftoverFhirPaths.removeAll(referencedFhirPaths);

        List<AbstractLintItem> items = createLintItems(
                projectDir, resourcesDir, leftoverBpmnPaths, leftoverFhirPaths);

        logAnalysisResults(
                actualBpmnPaths.size(), referencedBpmnPaths.size(), leftoverBpmnPaths.size(),
                actualFhirPaths.size(), referencedFhirPaths.size(), leftoverFhirPaths.size()
        );

        return new AnalysisResult(items, leftoverBpmnPaths, leftoverFhirPaths);
    }

    public List<AbstractLintItem> getItemsForPlugin(
            AnalysisResult leftoverAnalysis,
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin,
            boolean isLastPlugin,
            boolean isSinglePluginProject) {

        if (leftoverAnalysis == null || leftoverAnalysis.items().isEmpty()) {
            return Collections.emptyList();
        }

        if (isSinglePluginProject) {
            logger.debug("Single-plugin project: Returning all leftover items");
            return new ArrayList<>(leftoverAnalysis.items());
        }

        return handleMultiPluginLeftovers(
                leftoverAnalysis.items(),
                pluginName,
                plugin,
                isLastPlugin
        );
    }

    private List<AbstractLintItem> handleMultiPluginLeftovers(
            List<AbstractLintItem> allLeftoverItems,
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin,
            boolean isLastPlugin) {

        List<AbstractLintItem> result = new ArrayList<>();

        boolean hasLeftoverWarnings = allLeftoverItems.stream()
                .anyMatch(item -> item instanceof PluginLintItem pi &&
                        pi.getType() == LintingType.PLUGIN_DEFINITION_PROCESS_PLUGIN_RESOURCE_NOT_LOADED);

        if (!hasLeftoverWarnings) {
            if (isLastPlugin) {
                logger.debug("Multi-plugin project: Adding leftover success item to last plugin");
                result.addAll(allLeftoverItems);
            }
        } else {
            List<AbstractLintItem> pluginSpecificLeftovers = filterLeftoversForPlugin(
                    allLeftoverItems,
                    pluginName,
                    plugin
            );

            if (!pluginSpecificLeftovers.isEmpty()) {
                logger.debug("Multi-plugin project: Found " + pluginSpecificLeftovers.size() +
                        " plugin-specific leftovers for " + pluginName);
                result.addAll(pluginSpecificLeftovers);
            }

            if (isLastPlugin) {
                List<AbstractLintItem> unassignedLeftovers = getUnassignedLeftovers(
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

    private List<AbstractLintItem> filterLeftoversForPlugin(
            List<AbstractLintItem> allLeftovers,
            String pluginName,
            ResourceDiscoveryService.PluginDiscovery plugin) {

        return allLeftovers.stream()
                .filter(item -> {
                    if (item instanceof PluginLintItem leftoverItem &&
                            leftoverItem.getType() == LintingType.PLUGIN_DEFINITION_PROCESS_PLUGIN_RESOURCE_NOT_LOADED) {
                        String location = leftoverItem.getLocation();
                        String fileName = leftoverItem.getFileName();
                        String pluginNameLower = pluginName.toLowerCase();

                        return location.toLowerCase().contains(pluginNameLower) ||
                                fileName.toLowerCase().contains(pluginNameLower);
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    private List<AbstractLintItem> getUnassignedLeftovers(
            List<AbstractLintItem> allLeftovers,
            String excludePluginName) {

        return allLeftovers.stream()
                .filter(item -> {
                    if (item instanceof PluginLintItem leftoverItem &&
                            leftoverItem.getType() == LintingType.PLUGIN_DEFINITION_PROCESS_PLUGIN_RESOURCE_NOT_LOADED) {
                        String location = leftoverItem.getLocation();
                        String fileName = leftoverItem.getFileName();

                        return !location.toLowerCase().contains(excludePluginName.toLowerCase()) &&
                                !fileName.toLowerCase().contains(excludePluginName.toLowerCase());
                    }
                    return item instanceof PluginLintItem pi && pi.getSeverity() == LinterSeverity.SUCCESS;
                })
                .collect(Collectors.toList());
    }

    private List<AbstractLintItem> createLintItems(
            File projectDir,
            File resourcesDir,
            Set<String> leftoverBpmnPaths,
            Set<String> leftoverFhirPaths) {

        List<AbstractLintItem> items = new ArrayList<>();

        if (leftoverBpmnPaths.isEmpty() && leftoverFhirPaths.isEmpty()) {
            items.add(PluginLintItem.success(projectDir, projectDir.getName(),
                    "All BPMN and FHIR resources found in the project are correctly referenced in ProcessPluginDefinition."));
        } else {
            leftoverBpmnPaths.forEach(path -> {
                File file = new File(resourcesDir, path);
                items.add(new PluginLintItem(
                        LinterSeverity.WARN,
                        LintingType.PLUGIN_DEFINITION_PROCESS_PLUGIN_RESOURCE_NOT_LOADED,
                        file,
                        path,
                        "BPMN file exists but is not referenced in ProcessPluginDefinition"
                ));
            });

            leftoverFhirPaths.forEach(path -> {
                File file = new File(resourcesDir, path);
                items.add(new PluginLintItem(
                        LinterSeverity.WARN,
                        LintingType.PLUGIN_DEFINITION_PROCESS_PLUGIN_RESOURCE_NOT_LOADED,
                        file,
                        path,
                        "FHIR file exists but is not referenced in ProcessPluginDefinition"
                ));
            });
        }

        return items;
    }

    private List<File> findBpmnFilesRecursively(Path rootPath) {
        return findFiles(rootPath, "glob:**/*.bpmn");
    }

    private List<File> findFhirFilesRecursively(Path rootPath) {
        return findFiles(rootPath, "glob:**/*.{xml,json}").stream()
                .filter(f -> FhirFileUtils.isFhirFile(f.toPath()))
                .collect(Collectors.toList());
    }

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

    private String getRelativePath(File file, File baseDir) {
        String relative = baseDir.toPath().relativize(file.toPath()).toString();
        return relative.replace(File.separator, "/");
    }

    private void logAnalysisResults(
            int actualBpmn, int referencedBpmn, int leftoverBpmn,
            int actualFhir, int referencedFhir, int leftoverFhir) {

        logger.info("BPMN analysis: " + actualBpmn + " files found, " +
                referencedBpmn + " referenced, " + leftoverBpmn + " unused");

        logger.info("FHIR analysis: " + actualFhir + " files found, " +
                referencedFhir + " referenced, " + leftoverFhir + " unused");

        if (leftoverBpmn > 0 || leftoverFhir > 0) {
            logger.debug("Leftover resources will be displayed in linter output");
        }
    }

    public record AnalysisResult(
            List<AbstractLintItem> items,
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
