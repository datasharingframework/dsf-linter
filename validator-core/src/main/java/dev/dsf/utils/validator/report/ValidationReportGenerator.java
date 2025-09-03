package dev.dsf.utils.validator.report;

import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.service.BpmnValidationService;
import dev.dsf.utils.validator.service.FhirValidationService;
import dev.dsf.utils.validator.service.PluginValidationService;
import dev.dsf.utils.validator.util.validation.ValidationOutput;
import dev.dsf.utils.validator.ValidationSeverity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for generating validation reports.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Report directory preparation and cleanup</li>
 *   <li>Individual file report generation</li>
 *   <li>Aggregated report generation</li>
 *   <li>Report categorization by severity</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class ValidationReportGenerator {

    private final Logger logger;

    /**
     * Constructs a new ValidationReportGenerator.
     *
     * @param logger the logger for output messages
     */
    public ValidationReportGenerator(Logger logger) {
        this.logger = logger;
    }

    /**
     * Generates all validation reports.
     *
     * @param bpmnResults BPMN validation results
     * @param fhirResults FHIR validation results
     * @param pluginResults plugin validation results
     * @param leftoverResults leftover resource analysis results
     * @return the combined validation output
     * @throws IOException if report generation fails
     */
    public ValidationOutput generateReports(
            BpmnValidationService.ValidationResult bpmnResults,
            FhirValidationService.ValidationResult fhirResults,
            PluginValidationService.ValidationResult pluginResults,
            dev.dsf.utils.validator.analysis.LeftoverResourceDetector.AnalysisResult leftoverResults)
            throws IOException {

        // Prepare report directory
        File reportRoot = new File(System.getProperty("dsf.report.dir", "report"));
        prepareReportDirectory(reportRoot);

        // Generate BPMN reports
        generateBpmnReports(bpmnResults.getItems(), reportRoot);

        // Generate FHIR reports
        generateFhirReports(fhirResults.getItems(), reportRoot);

        // Generate plugin reports (includes leftover items)
        List<AbstractValidationItem> allPluginItems = new ArrayList<>();
        allPluginItems.addAll(leftoverResults.items());
        allPluginItems.addAll(pluginResults.getItems());
        generatePluginReports(allPluginItems, reportRoot);

        // Combine all items for aggregated report
        List<AbstractValidationItem> allItems = new ArrayList<>();
        allItems.addAll(bpmnResults.getItems());
        allItems.addAll(fhirResults.getItems());
        allItems.addAll(allPluginItems);

        // Generate aggregated report
        ValidationOutput output = new ValidationOutput(allItems);
        output.writeResultsAsJson(new File(reportRoot, "aggregated.json"));

        // Log summary
        logReportSummary(output, reportRoot);

        return output;
    }

    /**
     * Prepares the report directory by cleaning and creating it.
     *
     * @param reportRoot the report root directory
     * @throws IOException if preparation fails
     */
    private void prepareReportDirectory(File reportRoot) throws IOException {
        ReportCleaner.prepareCleanReportDirectory(reportRoot);
        logger.info("Report directory prepared: " + reportRoot.getAbsolutePath());
    }

    /**
     * Generates BPMN validation reports.
     *
     * @param items BPMN validation items
     * @param reportRoot the report root directory
     * @throws IOException if report generation fails
     */
    private void generateBpmnReports(List<AbstractValidationItem> items, File reportRoot)
            throws IOException {

        if (items.isEmpty()) {
            logger.info("No BPMN validation items to report.");
            return;
        }

        File bpmnFolder = new File(reportRoot, "bpmnReports");
        createReportStructure(items, bpmnFolder, "bpmn");
    }

    /**
     * Generates FHIR validation reports.
     *
     * @param items FHIR validation items
     * @param reportRoot the report root directory
     * @throws IOException if report generation fails
     */
    private void generateFhirReports(List<AbstractValidationItem> items, File reportRoot)
            throws IOException {

        if (items.isEmpty()) {
            logger.info("No FHIR validation items to report.");
            return;
        }

        File fhirFolder = new File(reportRoot, "fhirReports");
        createReportStructure(items, fhirFolder, "fhir");
    }

    /**
     * Generates plugin validation reports.
     *
     * @param items plugin validation items
     * @param reportRoot the report root directory
     * @throws IOException if report generation fails
     */
    private void generatePluginReports(List<AbstractValidationItem> items, File reportRoot)
            throws IOException {

        if (items.isEmpty()) {
            logger.info("No plugin validation items to report.");
            return;
        }

        File pluginFolder = new File(reportRoot, "pluginReports");
        createReportStructure(items, pluginFolder, "plugin");
    }

    /**
     * Creates the report structure with success/other folders and aggregated reports.
     *
     * @param items validation items to report
     * @param targetDir target directory for reports
     * @param prefix report prefix (e.g., "bpmn", "fhir", "plugin")
     * @throws IOException if directory creation fails
     */
    private void createReportStructure(
            List<AbstractValidationItem> items, File targetDir, String prefix)
            throws IOException {

        // Create directory structure
        if (!targetDir.mkdirs() && !targetDir.exists()) {
            throw new IOException("Failed to create directory: " + targetDir.getAbsolutePath());
        }

        File successDir = new File(targetDir, "success");
        File otherDir = new File(targetDir, "other");

        if (!successDir.mkdirs() && !successDir.exists()) {
            throw new IOException("Failed to create directory: " + successDir.getAbsolutePath());
        }

        if (!otherDir.mkdirs() && !otherDir.exists()) {
            throw new IOException("Failed to create directory: " + otherDir.getAbsolutePath());
        }

        // Separate items by severity
        List<AbstractValidationItem> successItems = items.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                .toList();

        List<AbstractValidationItem> otherItems = items.stream()
                .filter(item -> item.getSeverity() != ValidationSeverity.SUCCESS)
                .toList();

        // Write individual reports
        if ("plugin".equals(prefix)) {
            writeIndividualPluginReports(successItems, successDir, "plugin_issue_success_%03d.json");
            writeIndividualPluginReports(otherItems, otherDir, "plugin_issue_other_%03d.json");
        }

        // Write aggregated reports
        if (!successItems.isEmpty()) {
            new ValidationOutput(successItems).writeResultsAsJson(
                    new File(successDir, prefix + "_success_aggregated.json"));
        }

        if (!otherItems.isEmpty()) {
            new ValidationOutput(otherItems).writeResultsAsJson(
                    new File(otherDir, prefix + "_other_aggregated.json"));
        }

        // Write combined aggregated report
        if (!items.isEmpty()) {
            new ValidationOutput(items).writeResultsAsJson(
                    new File(targetDir, prefix + "_issues_aggregated.json"));
        }
    }

    /**
     * Writes individual plugin reports.
     *
     * @param items items to write
     * @param dir target directory
     * @param pattern filename pattern
     */
    private void writeIndividualPluginReports(
            List<AbstractValidationItem> items, File dir, String pattern) {

        for (int i = 0; i < items.size(); i++) {
            AbstractValidationItem item = items.get(i);
            String fileName = String.format(pattern, i + 1);
            List<AbstractValidationItem> singleItem = new ArrayList<>();
            singleItem.add(item);
            new ValidationOutput(singleItem).writeResultsAsJson(new File(dir, fileName));
        }
    }

    /**
     * Logs the report generation summary.
     *
     * @param output the validation output
     * @param reportRoot the report root directory
     */
    private void logReportSummary(ValidationOutput output, File reportRoot) {
        int totalIssues = output.validationItems().size();
        int errors = (int) output.validationItems().stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.ERROR)
                .count();
        int warnings = (int) output.validationItems().stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.WARN)
                .count();
        int successes = (int) output.validationItems().stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                .count();

        logger.info("Report generation complete:");
        logger.info("  Total issues: " + totalIssues);
        logger.info("  Errors: " + errors);
        logger.info("  Warnings: " + warnings);
        logger.info("  Successes: " + successes);
        logger.info("  Reports written to: " + reportRoot.getAbsolutePath());
    }
}