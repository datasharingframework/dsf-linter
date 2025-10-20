package dev.dsf.linter.service;

import dev.dsf.linter.item.*;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.fhir.FhirResourceValidator;
import dev.dsf.linter.exception.ResourceValidationException;
import dev.dsf.linter.util.validation.ValidationOutput;
import dev.dsf.linter.util.FileUtils;
import dev.dsf.linter.ValidationSeverity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for validating FHIR resources.
 *
 * <p>This service handles:
 * <ul>
 * <li>Individual FHIR file validation</li>
 * <li>Batch FHIR validation</li>
 * <li>Missing reference tracking</li>
 * <li>Validation result categorization</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class FhirValidationService {

    private final Logger logger;
    private final FhirResourceValidator fhirResourceValidator;

    /**
     * Constructs a new FhirValidationService.
     *
     * @param logger the logger for output messages
     */
    public FhirValidationService(Logger logger) {
        this.logger = logger;
        this.fhirResourceValidator = new FhirResourceValidator(logger);
    }

    /**
     * Validates all FHIR files and missing references for a given plugin.
     *
     * @param pluginName      The name of the plugin being validated.
     * @param fhirFiles       List of FHIR files to validate.
     * @param missingFhirRefs List of missing FHIR references.
     * @return Validation result containing all items.
     * @throws ResourceValidationException if any FHIR file contains parsing errors.
     */
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
     * Creates validation items for missing FHIR references.
     *
     * @param pluginName      The name of the plugin.
     * @param missingFhirRefs list of missing FHIR file references
     * @return list of validation items for missing references
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
     * Validates a single FHIR file and adds success items for successfully parsed files.
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
     * Extracts the FHIR reference from validation items.
     *
     * @param items validation items to search
     * @return the FHIR reference or "unknown_reference" if not found
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
     * Creates a success validation item for a found and readable FHIR file.
     *
     * @param fhirFile the FHIR file
     * @param fhirReference the extracted FHIR reference
     * @return a success validation item
     */
    private FhirElementValidationItemSuccess createFhirSuccessItem(File fhirFile, String fhirReference) {
        return new FhirElementValidationItemSuccess(
                fhirFile,
                fhirReference,
                "Referenced FHIR file found and is readable."
        );
    }

    /**
     * Creates individual file reports for FHIR validation.
     *
     * @param items validation items
     * @param fhirFile the FHIR file being validated
     * @return file report metadata
     */
    @Deprecated  // This method is no longer used and can be reused in future versions.
    public FileReportMetadata createFileReport(List<AbstractValidationItem> items, File fhirFile) {
        String baseName = FileUtils.getFileNameWithoutExtension(fhirFile);
        String parentFolderName = FileUtils.getParentFolderName(fhirFile);
        String fileName = "fhir_issues_" + parentFolderName + "_" + baseName + ".json";

        return new FileReportMetadata(fileName, items);
    }

    /**
     * Data class for file report metadata.
     */
    public record FileReportMetadata(String fileName, List<AbstractValidationItem> items) {
    }
}