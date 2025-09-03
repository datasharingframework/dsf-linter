package dev.dsf.utils.validator.service;

import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.fhir.FhirResourceValidator;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.validation.ValidationOutput;
import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for validating FHIR resources.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Individual FHIR file validation</li>
 *   <li>Batch FHIR validation</li>
 *   <li>Missing reference tracking</li>
 *   <li>Validation result categorization</li>
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
        this.fhirResourceValidator = new FhirResourceValidator();
    }

    /**
     * Validates all FHIR files and missing references.
     *
     * @param fhirFiles list of FHIR files to validate
     * @param missingFhirRefs list of missing FHIR references
     * @return validation result containing all items
     * @throws ResourceValidationException if any FHIR file contains parsing errors
     */
    public ValidationResult validate(List<File> fhirFiles, List<String> missingFhirRefs)
            throws ResourceValidationException {

        // Collect validation items for missing references
        List<AbstractValidationItem> allItems = new ArrayList<>(createMissingReferenceItems(missingFhirRefs));

        if (fhirFiles.isEmpty() && missingFhirRefs.isEmpty()) {
            logger.info("No referenced FHIR files to validate.");
            return new ValidationResult(allItems);
        }

        // Collect validation items for existing files
        allItems.addAll(validateExistingFiles(fhirFiles));

        return new ValidationResult(allItems);
    }

    /**
     * Creates validation items for missing FHIR references.
     *
     * @param missingFhirRefs list of missing FHIR file references
     * @return list of validation items for missing references
     */
    private List<AbstractValidationItem> createMissingReferenceItems(List<String> missingFhirRefs) {
        List<AbstractValidationItem> items = new ArrayList<>();

        for (String missing : missingFhirRefs) {
            FhirFileReferencedButNotFoundValidationItem notFoundItem =
                    new FhirFileReferencedButNotFoundValidationItem(
                            "Referenced FHIR file not found : " + missing,
                            ValidationSeverity.ERROR,
                            new File(missing) // only for context
                    );
            items.add(notFoundItem);
        }

        return items;
    }

    /**
     * Validates all existing FHIR files.
     *
     * @param fhirFiles list of FHIR files to validate
     * @return list of all validation items from file validation
     * @throws ResourceValidationException if any FHIR file contains parsing errors
     */
    private List<AbstractValidationItem> validateExistingFiles(List<File> fhirFiles)
            throws ResourceValidationException {
        List<AbstractValidationItem> allItems = new ArrayList<>();

        for (File fhirFile : fhirFiles) {
            List<AbstractValidationItem> fileItems = validateSingleFhirFile(fhirFile);
            allItems.addAll(fileItems);
        }

        return allItems;
    }

    /**
     * Validates a single FHIR file.
     *
     * @param fhirFile the FHIR file to validate
     * @return list of validation items for this file
     * @throws ResourceValidationException if the file contains parsing errors
     */
    private List<AbstractValidationItem> validateSingleFhirFile(File fhirFile)
            throws ResourceValidationException {

        logger.info("Validating FHIR file: " + fhirFile.getName());

        // Validate the file using FhirResourceValidator
        ValidationOutput output = fhirResourceValidator.validateSingleFile(fhirFile.toPath());
        List<AbstractValidationItem> itemsForThisFile = new ArrayList<>(output.validationItems());

        // Extract FHIR reference and add success item
        String fhirReference = extractFhirReference(itemsForThisFile);
        itemsForThisFile.add(createSuccessItem(fhirFile, fhirReference));

        // Print results for this file
        output.printResults();

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
    private FhirElementValidationItemSuccess createSuccessItem(File fhirFile, String fhirReference) {
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
    public FileReportMetadata createFileReport(List<AbstractValidationItem> items, File fhirFile) {      //this logic is ignored, but we can use it again, whenever we want
        String baseName = extractBaseName(fhirFile);
        String parentFolderName = extractParentFolderName(fhirFile);
        String fileName = "fhir_issues_" + parentFolderName + "_" + baseName + ".json";

        return new FileReportMetadata(fileName, items);
    }

    /**
     * Extracts the base name (without extension) from a file.
     *
     * @param file the file
     * @return base name without extension
     */
    private String extractBaseName(File file) {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return (idx > 0) ? name.substring(0, idx) : name;
    }

    /**
     * Extracts the parent folder name from a file.
     *
     * @param file the file
     * @return parent folder name or "root" if no parent
     */
    private String extractParentFolderName(File file) {
        return file.getParentFile() != null
                ? file.getParentFile().getName()
                : "root";
    }

    /**
     * Data class containing validation results.
     */
    public static class ValidationResult {
        private final List<AbstractValidationItem> items;
        private final int errorCount;
        private final int warningCount;
        private final int successCount;

        public ValidationResult(List<AbstractValidationItem> items) {
            this.items = items;

            this.errorCount = (int) items.stream()
                    .filter(item -> item.getSeverity() == ValidationSeverity.ERROR)
                    .count();

            this.warningCount = (int) items.stream()
                    .filter(item -> item.getSeverity() == ValidationSeverity.WARN)
                    .count();

            this.successCount = (int) items.stream()
                    .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                    .count();
        }

        public List<AbstractValidationItem> getItems() { return items; }
        public int getErrorCount() { return errorCount; }
        public int getWarningCount() { return warningCount; }
        public int getSuccessCount() { return successCount; }

        public boolean hasErrors() { return errorCount > 0; }
        public boolean hasWarnings() { return warningCount > 0; }

        public List<AbstractValidationItem> getSuccessItems() {
            return items.stream()
                    .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                    .toList();
        }

        public List<AbstractValidationItem> getNonSuccessItems() {
            return items.stream()
                    .filter(item -> item.getSeverity() != ValidationSeverity.SUCCESS)
                    .toList();
        }
    }

    /**
     * Data class for file report metadata.
     */
    public record FileReportMetadata(String fileName, List<AbstractValidationItem> items) {
    }
}