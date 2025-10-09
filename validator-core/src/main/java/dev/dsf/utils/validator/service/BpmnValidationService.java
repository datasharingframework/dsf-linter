package dev.dsf.utils.validator.service;

import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.bpmn.BPMNValidator;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.Console;
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
 * <li>Individual BPMN file validation</li>
 * <li>Batch BPMN validation</li>
 * <li>Missing reference tracking</li>
 * <li>Validation result categorization</li>
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
     * Validates all BPMN aspects for a specific plugin, including referenced but missing files
     * and the content of existing BPMN files. This method centralizes the collection of
     * all validation items and handles the console output for all BPMN-related issues.
     *
     * @param pluginName       The name of the plugin being validated. Used for context in error messages.
     * @param bpmnFiles        A list of existing BPMN files to be validated.
     * @param missingBpmnRefs  A list of BPMN file paths that were referenced but not found.
     * @return A {@link ValidationResult} object containing all collected validation items (errors and warnings).
     * @throws ResourceValidationException if a severe, unrecoverable error occurs during validation.
     */
    public ValidationResult validate(String pluginName, List<File> bpmnFiles, List<String> missingBpmnRefs)
            throws ResourceValidationException
    {
        List<AbstractValidationItem> allItems = new ArrayList<>();

        // Process existing files first (so "Validating BPMN file:" appears before summary)
        allItems.addAll(validateExistingFiles(bpmnFiles));

        // Then add missing reference errors
        allItems.addAll(createMissingReferenceItems(pluginName, missingBpmnRefs));

        if (!allItems.isEmpty())
        {
            ValidationOutput validationOutput = new ValidationOutput(allItems);
            long errorCount = validationOutput.getErrorCount();
            long warningCount = validationOutput.getWarningCount();
            long infoCount = validationOutput.getInfoCount();

            long nonSuccessCount = errorCount + warningCount + infoCount;

            logger.info("Found " + nonSuccessCount + " BPMN issue(s): (" + errorCount + " errors, " + warningCount + " warnings, " + infoCount + " infos)");

            getFilteredItems(allItems, logger);

        }

        return new ValidationResult(allItems);
    }

    static void getFilteredItems(List<AbstractValidationItem> allItems, Logger logger) {
        List<AbstractValidationItem> errors = allItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.ERROR)
                .toList();
        if (!errors.isEmpty())
        {
            Console.red("  ✗ ERROR items:");
            errors.forEach(e -> Console.red("    * " + e));
        }

        List<AbstractValidationItem> warnings = allItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.WARN)
                .toList();
        if (!warnings.isEmpty())
        {
            Console.yellow("  ⚠ WARN items:");
            warnings.forEach(w -> Console.yellow("    * " + w));
        }

        List<AbstractValidationItem> infos = allItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.INFO)
                .toList();
        if (!infos.isEmpty())
        {
            logger.info("  ℹ INFO items:");
            infos.forEach(i -> logger.info("    * " + i));
        }
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
     * Data class for file report metadata.
     */
    public record FileReportMetadata(String fileName, List<AbstractValidationItem> items) {
    }

}