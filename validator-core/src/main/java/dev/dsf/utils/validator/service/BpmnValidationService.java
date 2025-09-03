package dev.dsf.utils.validator.service;

import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.validation.ValidationOutput;
import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for validating BPMN resources.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Individual BPMN file validation</li>
 *   <li>Batch BPMN validation</li>
 *   <li>Missing reference tracking</li>
 *   <li>Validation result categorization</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class BpmnValidationService {

    private final Logger logger;
    private final BPMNValidator bpmnValidator;

    /**
     * Constructs a new BpmnValidationService.
     *
     * @param logger the logger for output messages
     */
    public BpmnValidationService(Logger logger) {
        this.logger = logger;
        this.bpmnValidator = new BPMNValidator();
    }

    /**
     * Validates all BPMN files and missing references.
     *
     * @param bpmnFiles list of BPMN files to validate
     * @param missingBpmnRefs list of missing BPMN references
     * @return validation result containing all items
     * @throws ResourceValidationException if any BPMN file contains parsing errors
     */
    public ValidationResult validate(List<File> bpmnFiles, List<String> missingBpmnRefs)
            throws ResourceValidationException {

        // Collect validation items for missing references
        List<AbstractValidationItem> allItems = new ArrayList<>(createMissingReferenceItems(missingBpmnRefs));

        if (bpmnFiles.isEmpty() && missingBpmnRefs.isEmpty()) {
            logger.info("No referenced .bpmn files to validate.");
            return new ValidationResult(allItems);
        }

        // Collect validation items for existing files
        allItems.addAll(validateExistingFiles(bpmnFiles));

        return new ValidationResult(allItems);
    }

    /**
     * Creates validation items for missing BPMN references.
     *
     * @param missingBpmnRefs list of missing BPMN file references
     * @return list of validation items for missing references
     */
    private List<AbstractValidationItem> createMissingReferenceItems(List<String> missingBpmnRefs) {
        List<AbstractValidationItem> items = new ArrayList<>();

        for (String missing : missingBpmnRefs) {
            BpmnFileReferencedButNotFoundValidationItem notFoundItem =
                    new BpmnFileReferencedButNotFoundValidationItem(
                            ValidationSeverity.ERROR,
                            new File(missing), // only for context
                            "Referenced BPMN file not found (disk or classpath): " + missing
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
     * @throws ResourceValidationException if any BPMN file contains parsing errors
     */
    private List<AbstractValidationItem> validateExistingFiles(List<File> bpmnFiles)
            throws ResourceValidationException {
        List<AbstractValidationItem> allItems = new ArrayList<>();

        for (File bpmnFile : bpmnFiles) {
            List<AbstractValidationItem> fileItems = validateSingleBpmnFile(bpmnFile);
            allItems.addAll(fileItems);
        }

        return allItems;
    }

    /**
     * Validates a single BPMN file.
     *
     * @param bpmnFile the BPMN file to validate
     * @return list of validation items for this file
     * @throws ResourceValidationException if the file contains parsing errors
     */
    private List<AbstractValidationItem> validateSingleBpmnFile(File bpmnFile)
            throws ResourceValidationException {

        logger.info("Validating BPMN file: " + bpmnFile.getName());

        // Validate the file using BPMNValidator
        ValidationOutput output = bpmnValidator.validateBpmnFile(bpmnFile.toPath());
        List<AbstractValidationItem> itemsForThisFile = new ArrayList<>(output.validationItems());

        // Extract process ID and add success item
        String processId = extractProcessId(output);
        itemsForThisFile.add(createSuccessItem(bpmnFile, processId));

        // Print results for this file
        output.printResults();

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
     * Creates a success validation item for a found and readable BPMN file.
     *
     * @param bpmnFile the BPMN file
     * @param processId the process ID
     * @return a success validation item
     */
    private BpmnElementValidationItemSuccess createSuccessItem(File bpmnFile, String processId) {
        return new BpmnElementValidationItemSuccess(
                processId,
                bpmnFile,
                processId,
                "Referenced BPMN file found and is readable."
        );
    }

    /**
     * Creates individual file reports for BPMN validation.
     *
     * @param items validation items
     * @param bpmnFile the BPMN file being validated
     * @param processId the process ID
     * @return file report metadata
     */
    public FileReportMetadata createFileReport(                   //this logic is ignored, but we can use it again, whenever we want
            List<AbstractValidationItem> items, File bpmnFile, String processId) {

        String normalizedProcessId = normalizeProcessIdForReport(processId, bpmnFile);
        String parentFolderName = extractParentFolderName(bpmnFile);
        String fileName = "bpmn_issues_" + parentFolderName + "_" + normalizedProcessId + ".json";

        return new FileReportMetadata(fileName, items);
    }

    /**
     * Normalizes the process ID for report naming.
     *
     * @param processId the process ID to normalize
     * @param bpmnFile the BPMN file (used as fallback for naming)
     * @return normalized process ID
     */
    private String normalizeProcessIdForReport(String processId, File bpmnFile) {
        if (processId == null || processId.isBlank()) {
            return extractBaseNameWithoutExtension(bpmnFile);
        }
        return processId;
    }

    /**
     * Extracts the base name without the specified extension from a file.
     *
     * @param file the file
     * @return base name without extension
     */
    private String extractBaseNameWithoutExtension(File file) {
        String name = file.getName();
        return name.endsWith(".bpmn")
                ? name.substring(0, name.length() - ".bpmn".length())
                : name;
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

        public List<AbstractValidationItem> getItems() {
            return items;
        }
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