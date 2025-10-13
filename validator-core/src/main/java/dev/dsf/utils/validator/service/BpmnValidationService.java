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
     * all validation items.
     *
     * @param pluginName       The name of the plugin being validated. Used for context in error messages.
     * @param bpmnFiles        A list of existing BPMN files to be validated.
     * @param missingBpmnRefs  A list of BPMN file paths that were referenced but not found.
     * @return A {@link ValidationResult} object containing all collected validation items (errors and warnings).
     * @throws ResourceValidationException if a severe, unrecoverable error occurs during validation.
     */
    public ValidationResult validate(String pluginName, List<File> bpmnFiles, List<String> missingBpmnRefs)
            throws ResourceValidationException {
        List<AbstractValidationItem> allItems = new ArrayList<>();

        // Process existing files first
        allItems.addAll(validateExistingFiles(pluginName, bpmnFiles));

        // Then add missing reference errors
        allItems.addAll(createMissingReferenceItems(pluginName, missingBpmnRefs));

        return new ValidationResult(allItems);
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
    private List<AbstractValidationItem> validateExistingFiles(String pluginName, List<File> bpmnFiles)
            throws ResourceValidationException {
        List<AbstractValidationItem> allItems = new ArrayList<>();

        for (File bpmnFile : bpmnFiles) {
            List<AbstractValidationItem> fileItems = validateSingleBpmnFile(pluginName, bpmnFile);
            allItems.addAll(fileItems);
        }

        return allItems;
    }

    /**
     * Validates a single BPMN file and adds success items for successfully parsed files.
     */
    private List<AbstractValidationItem> validateSingleBpmnFile(String pluginName, File bpmnFile)
            throws ResourceValidationException {

        logger.info("Validating BPMN file: " + bpmnFile.getName());

        // Validate the file using BPMNValidator
        ValidationOutput output = bpmnValidator.validateBpmnFile(bpmnFile.toPath());
        List<AbstractValidationItem> itemsForThisFile = new ArrayList<>(output.validationItems());

        // Check if file was successfully parsed (no Unparsable items)
        boolean hasUnparsableItem = itemsForThisFile.stream()
                .anyMatch(item -> item instanceof PluginDefinitionUnparsableBpmnResourceValidationItem);

        if (!hasUnparsableItem) {
            // Add PluginDefinitionValidationItemSuccess for successful parsing
            PluginDefinitionValidationItemSuccess pluginSuccessItem = new PluginDefinitionValidationItemSuccess(
                    bpmnFile,
                    pluginName,
                    String.format("BPMN file '%s' successfully parsed and validated for plugin '%s'",
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
     * Creates a BPMN-specific success validation item.
     */
    private BpmnElementValidationItemSuccess createBpmnSuccessItem(File bpmnFile, String processId) {
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
    public FileReportMetadata createFileReport(
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