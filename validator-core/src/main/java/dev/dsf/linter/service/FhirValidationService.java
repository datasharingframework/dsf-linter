package dev.dsf.linter.service;

import dev.dsf.linter.item.*;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.fhir.FhirResourceValidator;
import dev.dsf.linter.exception.ResourceValidationException;
import dev.dsf.linter.util.validation.ValidationOutput;
import dev.dsf.linter.util.resource.ResourceResolver;
import dev.dsf.linter.ValidationSeverity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for validating FHIR resources.
 * Enhanced with resource root validation.
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class FhirValidationService {

    private final Logger logger;
    private final FhirResourceValidator fhirResourceValidator;

    public FhirValidationService(Logger logger) {
        this.logger = logger;
        this.fhirResourceValidator = new FhirResourceValidator(logger);
    }

    /**
     * Validates all FHIR files and missing references for a given plugin.
     *
     * @deprecated Use {@link #validate(String, List, List, Map, File)} for enhanced resource root validation
     */
    @Deprecated
    public ValidationResult validate(String pluginName, List<File> fhirFiles, List<String> missingFhirRefs)
            throws ResourceValidationException {

        List<AbstractValidationItem> allItems = new ArrayList<>(createMissingReferenceItems(pluginName, missingFhirRefs));

        if (fhirFiles.isEmpty() && missingFhirRefs.isEmpty()) {
            logger.info("No referenced FHIR files to validate.");
            return new ValidationResult(allItems);
        }

        allItems.addAll(validateExistingFiles(pluginName, fhirFiles));

        return new ValidationResult(allItems);
    }

    /**
     * Enhanced validation method that includes resource root validation.
     * This is the new method that should be called from orchestrator.
     *
     * @param pluginName      The name of the plugin being validated
     * @param fhirFiles       List of FHIR files to validate
     * @param missingFhirRefs List of missing FHIR references
     * @param fhirOutsideRoot Map of FHIR files found outside expected resource root
     * @param pluginResourceRoot The plugin-specific resource root for success items
     * @return ValidationResult containing all items
     * @throws ResourceValidationException if any FHIR file contains parsing errors
     */
    public ValidationResult validate(
            String pluginName,
            List<File> fhirFiles,
            List<String> missingFhirRefs,
            Map<String, ResourceResolver.ResolutionResult> fhirOutsideRoot,
            File pluginResourceRoot)
            throws ResourceValidationException {

        List<AbstractValidationItem> allItems = new ArrayList<>();

        allItems.addAll(createMissingReferenceItems(pluginName, missingFhirRefs));
        allItems.addAll(validateExistingFiles(pluginName, fhirFiles));
        allItems.addAll(createOutsideRootItems(pluginName, fhirOutsideRoot));
        allItems.addAll(createSuccessItemsForValidResources(pluginName, fhirFiles, pluginResourceRoot));

        return new ValidationResult(allItems);
    }

    /**
     * Creates validation items for FHIR files found outside expected resource root.
     *
     * @param pluginName the plugin name
     * @param fhirOutsideRoot map of files found outside root
     * @return list of validation items
     */
    private List<AbstractValidationItem> createOutsideRootItems(
            String pluginName,
            Map<String, ResourceResolver.ResolutionResult> fhirOutsideRoot) {

        List<AbstractValidationItem> items = new ArrayList<>();

        if (fhirOutsideRoot == null || fhirOutsideRoot.isEmpty()) {
            return items;
        }

        for (Map.Entry<String, ResourceResolver.ResolutionResult> entry : fhirOutsideRoot.entrySet()) {
            String reference = entry.getKey();
            ResourceResolver.ResolutionResult result = entry.getValue();

            if (result.file().isPresent()) {
                items.add(new PluginDefinitionFhirFileReferencedFoundOutsideExpectedRootValidationItem(
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
     * Creates validation items for missing FHIR references.
     */
    private List<AbstractValidationItem> createMissingReferenceItems(String pluginName, List<String> missingFhirRefs) {
        List<AbstractValidationItem> items = new ArrayList<>();

        for (String missing : missingFhirRefs) {
            PluginDefinitionFhirFileReferencedButNotFoundValidationItem notFoundItem =
                    new PluginDefinitionFhirFileReferencedButNotFoundValidationItem(
                            pluginName,
                            ValidationSeverity.ERROR,
                            new File(missing),
                            "Referenced FHIR file not found"
                    );
            items.add(notFoundItem);
        }

        return items;
    }

    /**
     * Validates all existing FHIR files.
     */
    private List<AbstractValidationItem> validateExistingFiles(String pluginName, List<File> fhirFiles) {
        List<AbstractValidationItem> allItems = new ArrayList<>();

        for (File fhirFile : fhirFiles) {
            List<AbstractValidationItem> fileItems = validateSingleFhirFile(pluginName, fhirFile);
            allItems.addAll(fileItems);
        }

        return allItems;
    }

    /**
     * Validates a single FHIR file.
     */
    private List<AbstractValidationItem> validateSingleFhirFile(String pluginName, File fhirFile) {

        logger.info("Validating FHIR file: " + fhirFile.getName());

        ValidationOutput output = fhirResourceValidator.validateSingleFile(fhirFile.toPath());
        List<AbstractValidationItem> itemsForThisFile = new ArrayList<>(output.validationItems());

        boolean hasUnparsableItem = itemsForThisFile.stream()
                .anyMatch(item -> item instanceof PluginDefinitionUnparsableFhirResourceValidationItem);

        if (!hasUnparsableItem) {
            PluginDefinitionValidationItemSuccess pluginSuccessItem = new PluginDefinitionValidationItemSuccess(
                    fhirFile,
                    pluginName,
                    String.format("FHIR file '%s' successfully parsed and validated for plugin '%s'",
                            fhirFile.getName(), pluginName)
            );
            itemsForThisFile.add(pluginSuccessItem);
        }

        String fhirReference = extractFhirReference(itemsForThisFile);
        itemsForThisFile.add(createFhirSuccessItem(fhirFile, fhirReference));

        return itemsForThisFile;
    }

    /**
     * Extracts FHIR reference from validation items.
     */
    private String extractFhirReference(List<AbstractValidationItem> items) {
        return items.stream()
                .filter(item -> item instanceof FhirElementValidationItem)
                .map(item -> ((FhirElementValidationItem) item).getFhirReference())
                .filter(ref -> ref != null && !ref.isBlank())
                .findFirst()
                .orElse("unknown_reference");
    }

    /**
     * Creates success item for FHIR file.
     */
    private FhirElementValidationItemSuccess createFhirSuccessItem(File fhirFile, String fhirReference) {
        return new FhirElementValidationItemSuccess(
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
    private List<AbstractValidationItem> createSuccessItemsForValidResources(
            String pluginName,
            List<File> fhirFiles,
            File pluginResourceRoot) {

        List<AbstractValidationItem> items = new ArrayList<>();

        for (File fhirFile : fhirFiles) {
            items.add(new PluginDefinitionValidationItemSuccess(
                    fhirFile,
                    pluginName,
                    String.format("FHIR resource '%s' correctly located in resource root",
                            fhirFile.getName())
            ));
        }

        return items;
    }
}