package dev.dsf.linter.report;

import dev.dsf.linter.DsfValidatorImpl;
import dev.dsf.linter.ValidationSeverity;
import dev.dsf.linter.analysis.LeftoverResourceDetector;
import dev.dsf.linter.item.AbstractValidationItem;
import dev.dsf.linter.logger.LogDecorators;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.service.ResourceDiscoveryService;
import dev.dsf.linter.service.ValidationResult;
import dev.dsf.linter.util.Console;
import dev.dsf.linter.util.validation.ValidationUtils;
import dev.dsf.linter.util.validation.ValidationOutput;

import java.util.List;
import java.util.Map;

import static dev.dsf.linter.util.Console.ANSI_GREEN;

/**
 * Handles all console output for validation reports.
 * Responsible for printing headers, validation sections, and summaries to the console.
 */
public class ValidationConsolePrinter {

    private final Logger logger;

    public ValidationConsolePrinter(Logger logger) {
        this.logger = logger;
    }

    /**
     * Prints the validation header with project information.
     *
     * @param config The validator configuration
     */
    public void printHeader(DsfValidatorImpl.Config config) {
        logger.info("=".repeat(80));
        Console.bold("DSF Plugin Validation");
        logger.info("=".repeat(80));
        logger.info("Project: " + config.projectPath());
        logger.info("Report:  " + config.reportPath());
        logger.info("=".repeat(80));
    }

    /**
     * Prints a phase header for major validation steps.
     *
     * @param phaseName The name of the phase
     */
    public void printPhaseHeader(String phaseName) {
        logger.info("");
        logger.info("=".repeat(80));
        Console.cyan(phaseName);
        logger.info("=".repeat(80));
    }

    /**
     * Prints a header for individual plugin validation.
     *
     * @param pluginName The name of the plugin
     * @param current    Current plugin number
     * @param total      Total number of plugins
     */
    public void printPluginHeader(String pluginName, int current, int total) {
        LogDecorators.infoMint(logger, "-".repeat(80));
        LogDecorators.infoMint(logger,
                String.format("Validating Plugin [%d/%d]: %s", current, total, pluginName));
        LogDecorators.infoMint(logger, "-".repeat(80));
    }

    /**
     * Prints validation sections in fixed order: BPMN → FHIR → Plugin.
     * Each section shows counts and details of non-SUCCESS items.
     *
     * @param bpmnNonSuccess   Non-SUCCESS BPMN items
     * @param fhirNonSuccess   Non-SUCCESS FHIR items
     * @param pluginNonSuccess Non-SUCCESS Plugin items
     * @param pluginResult     Plugin validation result for ServiceLoader check
     * @param pluginNameShort  Short name of the plugin
     */
    public void printValidationSections(
            List<AbstractValidationItem> bpmnNonSuccess,
            List<AbstractValidationItem> fhirNonSuccess,
            List<AbstractValidationItem> pluginNonSuccess,
            ValidationResult pluginResult,
            String pluginNameShort) {

        printBpmnSection(bpmnNonSuccess);
        printFhirSection(fhirNonSuccess);
        printPluginSection(pluginNonSuccess, pluginResult, pluginNameShort);
    }

    /**
     * Prints the BPMN validation section.
     */
    private void printBpmnSection(List<AbstractValidationItem> bpmnNonSuccess) {
        ValidationUtils.SeverityCount bpmnCount = ValidationUtils.countSeverities(bpmnNonSuccess);
        LogDecorators.infoMint(logger,
                "Found " + bpmnCount.getTotal() + " BPMN issue(s): (" +
                        bpmnCount.getErrors() + " errors, " +
                        bpmnCount.getWarnings() + " warnings, " +
                        bpmnCount.getInfos() + " infos)");

        if (!bpmnNonSuccess.isEmpty()) {
            printItemsByType(bpmnNonSuccess, "BPMN");
        }
    }

    /**
     * Prints the FHIR validation section.
     */
    private void printFhirSection(List<AbstractValidationItem> fhirNonSuccess) {
        ValidationUtils.SeverityCount fhirCount = ValidationUtils.countSeverities(fhirNonSuccess);
        LogDecorators.infoMint(logger,
                "Found " + fhirCount.getTotal() + " FHIR issue(s): (" +
                        fhirCount.getErrors() + " errors, " +
                        fhirCount.getWarnings() + " warnings, " +
                        fhirCount.getInfos() + " infos)");

        if (!fhirNonSuccess.isEmpty()) {
            printItemsByType(fhirNonSuccess, "FHIR");
        }
    }

    /**
     * Prints the Plugin validation section.
     */
    private void printPluginSection(
            List<AbstractValidationItem> pluginNonSuccess,
            ValidationResult pluginResult,
            String pluginNameShort) {

        ValidationUtils.SeverityCount pluginCount = ValidationUtils.countSeverities(pluginNonSuccess);
        LogDecorators.infoMint(logger,
                "Found " + pluginCount.getTotal() + " Plugin issue(s) for: " + pluginNameShort +
                        ": (" + pluginCount.getErrors() + " errors, " +
                        pluginCount.getWarnings() + " warnings, " +
                        pluginCount.getInfos() + " infos)");

        if (!pluginNonSuccess.isEmpty()) {
            printItemsByType(pluginNonSuccess, "Plugin");
        }

        printServiceLoaderStatus(pluginResult);
    }

    /**
     * Prints the ServiceLoader registration status.
     */
    private void printServiceLoaderStatus(ValidationResult pluginResult) {
        boolean hasServiceLoaderSuccess =
                pluginResult.getItems().stream()
                        .anyMatch(i -> i.getClass().getSimpleName().equals("PluginDefinitionValidationItemSuccess"));

        boolean hasServiceLoaderMissing =
                pluginResult.getItems().stream()
                        .anyMatch(i -> i.getClass().getSimpleName().equals("PluginDefinitionMissingServiceLoaderRegistrationValidationItem"));

        if (hasServiceLoaderSuccess) {
            Console.green("✓ ServiceLoader registration verified");
        } else if (hasServiceLoaderMissing) {
            Console.red("✗ ServiceLoader registration missing");
        } else {
            Console.yellow("⚠ ServiceLoader registration: not verified explicitly");
        }
    }

    /**
     * Prints validation items grouped by severity for a specific type (BPMN, FHIR, or Plugin).
     * Only prints non-SUCCESS items.
     *
     * @param items    The items to print
     * @param typeName The name of the type (e.g., "BPMN", "FHIR", "Plugin")
     */
    private void printItemsByType(List<AbstractValidationItem> items, String typeName) {
        List<AbstractValidationItem> nonSuccessItems = items.stream()
                .filter(item -> item.getSeverity() != ValidationSeverity.SUCCESS)
                .toList();

        if (nonSuccessItems.isEmpty()) {
            return;
        }

        List<AbstractValidationItem> errors = ValidationUtils.filterBySeverity(nonSuccessItems, ValidationSeverity.ERROR);
        List<AbstractValidationItem> warnings = ValidationUtils.filterBySeverity(nonSuccessItems, ValidationSeverity.WARN);
        List<AbstractValidationItem> infos = ValidationUtils.filterBySeverity(nonSuccessItems, ValidationSeverity.INFO);

        printErrorItems(errors);
        printWarningItems(warnings);
        printInfoItems(infos);
    }

    /**
     * Prints ERROR items.
     */
    private void printErrorItems(List<AbstractValidationItem> errors) {
        if (!errors.isEmpty()) {
            Console.red("  ✗ ERROR items:");
            for (AbstractValidationItem item : errors) {
                Console.red("    * " + formatItemMessage(item));
            }
        }
    }

    /**
     * Prints WARN items.
     */
    private void printWarningItems(List<AbstractValidationItem> warnings) {
        if (!warnings.isEmpty()) {
            Console.yellow("  ⚠ WARN items:");
            for (AbstractValidationItem item : warnings) {
                Console.yellow("    * " + formatItemMessage(item));
            }
        }
    }

    /**
     * Prints INFO items.
     */
    private void printInfoItems(List<AbstractValidationItem> infos) {
        if (!infos.isEmpty()) {
            logger.info("  ℹ INFO items:");
            for (AbstractValidationItem item : infos) {
                logger.info("    * " + formatItemMessage(item));
            }
        }
    }

    /**
     * Formats a validation item message for display.
     * Removes redundant severity prefix if present in toString().
     *
     * @param item The validation item to format
     * @return The formatted message
     */
    private String formatItemMessage(AbstractValidationItem item) {
        String message = item.toString();
        String severityPrefix = "[" + item.getSeverity() + "] ";
        if (message.startsWith(severityPrefix)) {
            message = message.substring(severityPrefix.length());
        }
        return message;
    }

    /**
     * Prints a summary of the validation results for a single plugin.
     * ALWAYS displays counts for errors, warnings, infos, and SUCCESS items.
     * Displays detailed SUCCESS items only in verbose mode.
     *
     * @param output The validation output to summarize.
     */
    public void printPluginSummary(ValidationOutput output) {
        logger.info("");
        logger.info("Plugin validation summary:");
        logger.info("");

        int errorCount = output.getErrorCount();
        int warningCount = output.getWarningCount();
        int infoCount = output.getInfoCount();
        int successCount = output.getSuccessCount();

        printErrorSummary(errorCount);
        printWarningSummary(warningCount);
        printInfoSummary(infoCount);
        printSuccessSummary(successCount);

        if (logger.isVerbose() && successCount > 0) {
            printDetailedSuccessItems(output);
        }
    }

    /**
     * Prints error count summary line.
     */
    private void printErrorSummary(int errorCount) {
        if (errorCount > 0) {
            System.out.println("\u001B[31m  ✗ Errors:   " + errorCount + "\u001B[0m");
            System.out.flush();
        } else {
            System.out.println(ANSI_GREEN + "  ✓ Errors:   " + errorCount + "\u001B[0m");
            System.out.flush();
        }
    }

    /**
     * Prints warning count summary line.
     */
    private void printWarningSummary(int warningCount) {
        if (warningCount > 0) {
            Console.yellow("  ⚠ Warnings: " + warningCount);
        } else {
            logger.info("  ✓ Warnings: " + warningCount);
        }
    }

    /**
     * Prints info count summary line.
     */
    private void printInfoSummary(int infoCount) {
        if (infoCount > 0) {
            logger.info("  ℹ Infos:    " + infoCount);
        } else {
            logger.info("  ✓ Infos:    " + infoCount);
        }
    }

    /**
     * Prints success count summary line.
     */
    private void printSuccessSummary(int successCount) {
        if (successCount > 0) {
            Console.green("  ✓ Success:  " + successCount);
        } else {
            logger.info("  ✓ Success:  " + successCount);
        }
    }

    /**
     * Prints detailed SUCCESS items in verbose mode.
     */
    private void printDetailedSuccessItems(ValidationOutput output) {
        logger.info("");
        Console.green("  Detailed SUCCESS items (" + output.getSuccessCount() + "):");

        output.validationItems().stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                .forEach(item -> Console.green("    * " + item));
    }

    /**
     * Prints the final validation summary with statistics and results.
     *
     * @param validations     All plugin validations
     * @param discovery       Resource discovery results
     * @param leftoverResults Leftover analysis results
     * @param executionTime   Total execution time in milliseconds
     * @param config          Validator configuration
     */
    public void printSummary(
            Map<String, DsfValidatorImpl.PluginValidation> validations,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            long executionTime,
            DsfValidatorImpl.Config config) {

        printSummaryHeader();
        printProjectAnalysisSummary(validations, discovery);
        printValidationResultsSummary(validations, leftoverResults);
        printExecutionSummary(executionTime, config);
        printFinalResult(validations, config);
    }

    /**
     * Prints the summary header.
     */
    private void printSummaryHeader() {
        logger.info("");
        logger.info("=".repeat(80));
        Console.bold("Validation Complete!");
        logger.info("=".repeat(80));
        logger.info("");
        logger.info("--- Project Analysis Summary ---");
        logger.info("");
    }

    /**
     * Prints project analysis statistics.
     */
    private void printProjectAnalysisSummary(
            Map<String, DsfValidatorImpl.PluginValidation> validations,
            ResourceDiscoveryService.DiscoveryResult discovery) {

        var stats = discovery.getStatistics();
        logger.info(String.format(
                "Resources Found: %d BPMN, %d FHIR (%d missing BPMN, %d missing FHIR)",
                stats.bpmnFiles(), stats.fhirFiles(),
                stats.missingBpmn(), stats.missingFhir()
        ));

        logger.info(String.format("Plugins Found:   %d", validations.size()));
        logger.info("");
    }

    /**
     * Prints validation results summary.
     */
    private void printValidationResultsSummary(
            Map<String, DsfValidatorImpl.PluginValidation> validations,
            LeftoverResourceDetector.AnalysisResult leftoverResults) {

        int totalPluginErrors = validations.values().stream().mapToInt(v -> v.output().getErrorCount()).sum();
        int totalPluginWarnings = validations.values().stream().mapToInt(v -> v.output().getWarningCount()).sum();
        int totalPluginInfos = validations.values().stream().mapToInt(v -> v.output().getInfoCount()).sum();
        int totalPluginSuccess = validations.values().stream().mapToInt(v -> v.output().getSuccessCount()).sum();
        int totalLeftovers = (leftoverResults != null) ? leftoverResults.getTotalLeftoverCount() : 0;

        if (totalPluginErrors > 0) {
            Console.red(String.format("✗ Plugin Errors:   %d", totalPluginErrors));
        } else {
            Console.green("✓ Plugin Errors:   0");
        }

        if (totalPluginWarnings > 0) {
            Console.yellow(String.format("⚠ Plugin Warnings: %d", totalPluginWarnings));
        } else {
            logger.info("  Plugin Warnings: 0");
        }

        if (totalPluginInfos > 0) {
            logger.info(String.format("ℹ Plugin Infos:    %d", totalPluginInfos));
        } else {
            logger.info("  Plugin Infos:    0");
        }

        if (totalPluginSuccess > 0) {
            Console.green(String.format("✓ Plugin Success:  %d", totalPluginSuccess));
        } else {
            logger.info("  Plugin Success:  0");
        }

        if (totalLeftovers > 0) {
            Console.yellow(String.format("⚠ Unreferenced:    %d files (project-wide)", totalLeftovers));
        }
    }

    /**
     * Prints execution time and report location.
     */
    private void printExecutionSummary(long executionTime, DsfValidatorImpl.Config config) {
        logger.info("");
        logger.info(String.format("Time:            %.2f seconds", executionTime / 1000.0));
        logger.info(String.format("Reports:         %s", config.reportPath().toAbsolutePath()));
        logger.info("=".repeat(80));
    }

    /**
     * Prints the final validation result (SUCCESS or FAILED).
     */
    private void printFinalResult(
            Map<String, DsfValidatorImpl.PluginValidation> validations,
            DsfValidatorImpl.Config config) {

        int totalPluginErrors = validations.values().stream().mapToInt(v -> v.output().getErrorCount()).sum();

        if (config.failOnErrors() && totalPluginErrors > 0) {
            Console.red("Result: FAILED (" + totalPluginErrors + " plugin errors found)");
        } else {
            Console.green("Result: SUCCESS");
        }

        logger.info("=".repeat(80));
    }
}