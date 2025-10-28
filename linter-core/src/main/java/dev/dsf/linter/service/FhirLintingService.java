package dev.dsf.linter.service;

import dev.dsf.linter.exception.ResourceLinterException;
import dev.dsf.linter.fhir.FhirResourceLinter;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.util.linting.LintingUtils;
import dev.dsf.linter.util.resource.ResourceResolver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for linting FHIR resources.
 * Enhanced with resource root linting and dependency JAR support.
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class FhirLintingService {

    private final Logger logger;
    private final FhirResourceLinter fhirResourceLinter;

    public FhirLintingService(Logger logger) {
        this.logger = logger;
        this.fhirResourceLinter = new FhirResourceLinter(logger);
    }

    /**
     * lints all FHIR files and missing references for a given plugin.
     *
     * @deprecated Use {@link #lint(String, List, List, Map, Map, File)} for enhanced resource root and dependency validation
     */
    @Deprecated
    public LintingResult lint(String pluginName, List<File> fhirFiles, List<String> missingFhirRefs)
            throws ResourceLinterException {

        List<AbstractLintItem> allItems = new ArrayList<>(createMissingReferenceItems(pluginName, missingFhirRefs));

        if (fhirFiles.isEmpty() && missingFhirRefs.isEmpty()) {
            logger.info("No referenced FHIR files to lint.");
            return new LintingResult(allItems);
        }

        allItems.addAll(lintExistingFiles(pluginName, fhirFiles));

        return new LintingResult(allItems);
    }

    /**
     * Enhanced lint method that includes resource root linting and dependency JAR support.
     * This is the new method that should be called from orchestrator.
     *
     * @param pluginName          The name of the plugin being linted
     * @param fhirFiles           List of FHIR files to lint (from project or dependencies)
     * @param missingFhirRefs     List of missing FHIR references
     * @param fhirOutsideRoot     Map of FHIR files found outside expected resource root
     * @param fhirFromDependencies Map of FHIR files found in dependency JARs
     * @param pluginResourceRoot  The plugin-specific resource root for success items
     * @return LintingResult containing all items
     * @throws ResourceLinterException if any FHIR file contains parsing errors
     */
    public LintingResult lint(
            String pluginName,
            List<File> fhirFiles,
            List<String> missingFhirRefs,
            Map<String, ResourceResolver.ResolutionResult> fhirOutsideRoot,
            Map<String, ResourceResolver.ResolutionResult> fhirFromDependencies,
            File pluginResourceRoot)
            throws ResourceLinterException {

        List<AbstractLintItem> allItems = new ArrayList<>();

        allItems.addAll(createMissingReferenceItems(pluginName, missingFhirRefs));
        allItems.addAll(lintExistingFiles(pluginName, fhirFiles));
        allItems.addAll(createOutsideRootItems(pluginName, fhirOutsideRoot));
        allItems.addAll(createDependencyItems(pluginName, fhirFromDependencies));
        allItems.addAll(createSuccessItemsForValidResources(pluginName, fhirFiles, pluginResourceRoot));

        return new LintingResult(allItems);
    }

    /**
     * Creates lint items for FHIR files found outside expected resource root.
     *
     * @param pluginName the plugin name
     * @param fhirOutsideRoot map of files found outside root
     * @return list of lint items
     */
    private List<AbstractLintItem> createOutsideRootItems(
            String pluginName,
            Map<String, ResourceResolver.ResolutionResult> fhirOutsideRoot) {

        List<AbstractLintItem> items = new ArrayList<>();

        if (fhirOutsideRoot == null || fhirOutsideRoot.isEmpty()) {
            return items;
        }

        for (Map.Entry<String, ResourceResolver.ResolutionResult> entry : fhirOutsideRoot.entrySet()) {
            String reference = entry.getKey();
            ResourceResolver.ResolutionResult result = entry.getValue();

            if (result.file().isPresent()) {
                items.add(new PluginDefinitionFhirFileReferencedFoundOutsideExpectedRootLintItem(
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
     * Creates INFO lint items for FHIR files found in dependency JARs.
     * These are treated as SUCCESS since dependencies are expected to provide resources.
     *
     * @param pluginName the plugin name
     * @param fhirFromDependencies map of files found in dependencies
     * @return list of INFO lint items
     */
    private List<AbstractLintItem> createDependencyItems(
            String pluginName,
            Map<String, ResourceResolver.ResolutionResult> fhirFromDependencies) {

        return LintingUtils.createDependencySuccessItems(
                pluginName,
                fhirFromDependencies,
                "FHIR"
        );
    }

    /**
     * Creates lint items for missing FHIR references.
     */
    private List<AbstractLintItem> createMissingReferenceItems(String pluginName, List<String> missingFhirRefs) {
        List<AbstractLintItem> items = new ArrayList<>();

        for (String missing : missingFhirRefs) {
            PluginDefinitionFhirFileReferencedButNotFoundLintItem notFoundItem =
                    new PluginDefinitionFhirFileReferencedButNotFoundLintItem(
                            pluginName,
                            LinterSeverity.ERROR,
                            new File(missing),
                            "Referenced FHIR file not found"
                    );
            items.add(notFoundItem);
        }

        return items;
    }

    /**
     * lints all existing FHIR files.
     */
    private List<AbstractLintItem> lintExistingFiles(String pluginName, List<File> fhirFiles) {
        List<AbstractLintItem> allItems = new ArrayList<>();

        for (File fhirFile : fhirFiles) {
            List<AbstractLintItem> fileItems = lintSingleFhirFile(pluginName, fhirFile);
            allItems.addAll(fileItems);
        }

        return allItems;
    }

    /**
     * lints a single FHIR file.
     */
    private List<AbstractLintItem> lintSingleFhirFile(String pluginName, File fhirFile) {

        logger.info("Linting FHIR file: " + fhirFile.getName());

        LintingOutput output = fhirResourceLinter.lintSingleFile(fhirFile.toPath());
        List<AbstractLintItem> itemsForThisFile = new ArrayList<>(output.LintItems());

        boolean hasUnparsableItem = itemsForThisFile.stream()
                .anyMatch(item -> item instanceof PluginDefinitionUnparsableFhirResourceLintItem);

        if (!hasUnparsableItem) {
            PluginDefinitionLintItemSuccess pluginSuccessItem = new PluginDefinitionLintItemSuccess(
                    fhirFile,
                    pluginName,
                    String.format("FHIR file '%s' successfully parsed and linted for plugin '%s'",
                            fhirFile.getName(), pluginName)
            );
            itemsForThisFile.add(pluginSuccessItem);
        }

        String fhirReference = extractFhirReference(itemsForThisFile);
        itemsForThisFile.add(createFhirSuccessItem(fhirFile, fhirReference));

        return itemsForThisFile;
    }

    /**
     * Extracts FHIR reference from lint items.
     */
    private String extractFhirReference(List<AbstractLintItem> items) {
        return items.stream()
                .filter(item -> item instanceof FhirElementLintItem)
                .map(item -> ((FhirElementLintItem) item).getFhirReference())
                .filter(ref -> ref != null && !ref.isBlank())
                .findFirst()
                .orElse("unknown_reference");
    }

    /**
     * Creates success item for FHIR file.
     */
    private FhirElementLintItemSuccess createFhirSuccessItem(File fhirFile, String fhirReference) {
        return new FhirElementLintItemSuccess(
                fhirFile,
                fhirReference,
                "Referenced FHIR file found and is readable."
        );
    }

    /**
     * Creates success items for FHIR resources that are correctly located in resource root.
     * One success item per valid resource.
     *
     * @param pluginName the plugin name
     * @param fhirFiles list of valid FHIR files
     * @param pluginResourceRoot the plugin's resource root
     * @return list of success validation items
     */
    private List<AbstractLintItem> createSuccessItemsForValidResources(
            String pluginName,
            List<File> fhirFiles,
            File pluginResourceRoot) {

        List<AbstractLintItem> items = new ArrayList<>();

        for (File fhirFile : fhirFiles) {
            items.add(new PluginDefinitionLintItemSuccess(
                    fhirFile,
                    pluginName,
                    String.format("FHIR resource '%s' correctly located in resource root",
                            fhirFile.getName())
            ));
        }

        return items;
    }
}