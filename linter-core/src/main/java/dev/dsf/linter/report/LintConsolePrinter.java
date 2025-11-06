package dev.dsf.linter.report;

import dev.dsf.linter.DsfLinter;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.analysis.LeftoverResourceDetector;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.logger.LogDecorators;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.service.LintingResult;
import dev.dsf.linter.service.ResourceDiscoveryService;
import dev.dsf.linter.logger.Console;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.util.linting.LintingUtils;

import java.util.List;
import java.util.Map;

/**
 * Handles all console output for linter reports.
 * Responsible for printing headers, linter sections, and summaries to the console.
 */
public class LintConsolePrinter {

    private final Logger logger;

    public LintConsolePrinter(Logger logger) {
        this.logger = logger;
    }

    /**
     * Prints the linter header with project information.
     *
     * @param config The linter configuration
     */
    public void printHeader(DsfLinter.Config config) {
        logger.info("=".repeat(80));
        Console.bold("DSF Plugin Linter");
        logger.info("=".repeat(80));
        logger.info("Project: " + config.projectPath());
        logger.info("Report:  " + config.reportPath());
        logger.info("=".repeat(80));
    }

    /**
     * Prints a phase header for major linting steps.
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
     * Prints a header for individual plugin linter.
     *
     * @param pluginName The name of the plugin
     * @param current    Current plugin number
     * @param total      Total number of plugins
     */
    public void printPluginHeader(String pluginName, int current, int total) {
        LogDecorators.infoMint(logger, "-".repeat(80));
        LogDecorators.infoMint(logger,
                String.format("Linting Plugin [%d/%d]: %s", current, total, pluginName));
        LogDecorators.infoMint(logger, "-".repeat(80));
    }

    /**
     * Prints lints sections in fixed order: BPMN → FHIR → Plugin.
     * Each section shows counts and details of non-SUCCESS items.
     *
     * @param bpmnNonSuccess   Non-SUCCESS BPMN items
     * @param fhirNonSuccess   Non-SUCCESS FHIR items
     * @param pluginNonSuccess Non-SUCCESS Plugin items
     * @param pluginResult     Plugin linting result for ServiceLoader check
     * @param pluginNameShort  Short name of the plugin
     */
    public void printLintsSections(
            List<AbstractLintItem> bpmnNonSuccess,
            List<AbstractLintItem> fhirNonSuccess,
            List<AbstractLintItem> pluginNonSuccess,
            LintingResult pluginResult,
            String pluginNameShort) {

        printBpmnSection(bpmnNonSuccess);
        printFhirSection(fhirNonSuccess);
        printPluginSection(pluginNonSuccess, pluginResult, pluginNameShort);
    }

    /**
     * Prints the BPMN lints section.
     */
    private void printBpmnSection(List<AbstractLintItem> bpmnNonSuccess) {
        LintingUtils.SeverityCount bpmnCount = LintingUtils.countSeverities(bpmnNonSuccess);
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
     * Prints the FHIR lints section.
     */
    private void printFhirSection(List<AbstractLintItem> fhirNonSuccess) {
        LintingUtils.SeverityCount fhirCount = LintingUtils.countSeverities(fhirNonSuccess);
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
     * Prints the Plugin lints section.
     */
    private void printPluginSection(
            List<AbstractLintItem> pluginNonSuccess,
            LintingResult pluginResult,
            String pluginNameShort) {

        LintingUtils.SeverityCount pluginCount = LintingUtils.countSeverities(pluginNonSuccess);
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
    private void printServiceLoaderStatus(LintingResult pluginResult) {
        boolean hasServiceLoaderSuccess =
                pluginResult.getItems().stream()
                        .anyMatch(i -> i.getClass().getSimpleName().equals("PluginDefinitionLintItemSuccess"));

        boolean hasServiceLoaderMissing =
                pluginResult.getItems().stream()
                        .anyMatch(i -> i.getClass().getSimpleName().equals("PluginDefinitionMissingServiceLoaderRegistrationLintItem"));

        if (hasServiceLoaderSuccess) {
            Console.green("✓ ServiceLoader registration verified");
        } else if (hasServiceLoaderMissing) {
            Console.red("✗ ServiceLoader registration missing");
        } else {
            Console.yellow("⚠ ServiceLoader registration: not verified explicitly");
        }
    }

    /**
     * Prints lint items grouped by severity for a specific type (BPMN, FHIR, or Plugin).
     * Only prints non-SUCCESS items.
     *
     * @param items    The items to print
     * @param typeName The name of the type (e.g., "BPMN", "FHIR", "Plugin")
     */
    private void printItemsByType(List<AbstractLintItem> items, String typeName) {
        List<AbstractLintItem> nonSuccessItems = items.stream()
                .filter(item -> item.getSeverity() != LinterSeverity.SUCCESS)
                .toList();

        if (nonSuccessItems.isEmpty()) {
            return;
        }

        List<AbstractLintItem> errors = LintingUtils.filterBySeverity(nonSuccessItems, LinterSeverity.ERROR);
        List<AbstractLintItem> warnings = LintingUtils.filterBySeverity(nonSuccessItems, LinterSeverity.WARN);
        List<AbstractLintItem> infos = LintingUtils.filterBySeverity(nonSuccessItems, LinterSeverity.INFO);

        printErrorItems(errors);
        printWarningItems(warnings);
        printInfoItems(infos);
    }

    /**
     * Prints ERROR items.
     */
    private void printErrorItems(List<AbstractLintItem> errors) {
        if (!errors.isEmpty()) {
            Console.red("  ✗ ERROR items:");
            for (AbstractLintItem item : errors) {
                Console.red("    * " + formatItemMessage(item));
            }
        }
    }

    /**
     * Prints WARN items.
     */
    private void printWarningItems(List<AbstractLintItem> warnings) {
        if (!warnings.isEmpty()) {
            Console.yellow("  ⚠ WARN items:");
            for (AbstractLintItem item : warnings) {
                Console.yellow("    * " + formatItemMessage(item));
            }
        }
    }

    /**
     * Prints INFO items.
     */
    private void printInfoItems(List<AbstractLintItem> infos) {
        if (!infos.isEmpty()) {
            logger.info("  ℹ INFO items:");
            for (AbstractLintItem item : infos) {
                logger.info("    * " + formatItemMessage(item));
            }
        }
    }

    /**
     * Formats a lint item message for display.
     * Removes redundant severity prefix if present in toString().
     *
     * @param item The lint item to format
     * @return The formatted message
     */
    private String formatItemMessage(AbstractLintItem item) {
        String message = item.toString();
        String severityPrefix = "[" + item.getSeverity() + "] ";
        if (message.startsWith(severityPrefix)) {
            message = message.substring(severityPrefix.length());
        }
        return message;
    }

    /**
     * Prints a summary of the linting results for a single plugin.
     * ALWAYS displays counts for errors, warnings, infos, and SUCCESS items.
     * Displays detailed SUCCESS items only in verbose mode.
     *
     * @param output The linting output to summarize.
     */
    public void printPluginSummary(LintingOutput output) {
        logger.info("");
        logger.info("Plugin lints summary:");
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
            Console.red("  ✗ Errors:   " + errorCount);
        } else {
            Console.green("  ✓ Errors:   " + errorCount);
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
    private void printDetailedSuccessItems(LintingOutput output) {
        logger.info("");
        Console.green("  Detailed SUCCESS items (" + output.getSuccessCount() + "):");

        output.LintItems().stream()
                .filter(item -> item.getSeverity() == LinterSeverity.SUCCESS)
                .forEach(item -> Console.green("    * " + item));
    }

    /**
     * Prints the final lints summary with statistics and results.
     *
     * @param lints     All plugin lints
     * @param discovery       Resource discovery results
     * @param leftoverResults Leftover analysis results
     * @param executionTime   Total execution time in milliseconds
     * @param config          Linter configuration
     */
    public void printSummary(
            Map<String, DsfLinter.PluginLinter> lints,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            long executionTime,
            DsfLinter.Config config) {

        printSummaryHeader();
        printProjectAnalysisSummary(lints, discovery);
        printLintsResultsSummary(lints, leftoverResults);
        printExecutionSummary(executionTime, config);
        printFinalResult(lints, config);
    }

    /**
     * Prints the summary header.
     */
    private void printSummaryHeader() {
        logger.info("");
        logger.info("=".repeat(80));
        Console.bold("Linting Complete!");
        logger.info("=".repeat(80));
        logger.info("");
        logger.info("--- Project Analysis Summary ---");
        logger.info("");
    }

    /**
     * Prints project analysis statistics.
     */
    private void printProjectAnalysisSummary(
            Map<String, DsfLinter.PluginLinter> lints,
            ResourceDiscoveryService.DiscoveryResult discovery) {

        var stats = discovery.getStatistics();
        logger.info(String.format(
                "Resources Found: %d BPMN, %d FHIR (%d missing BPMN, %d missing FHIR)",
                stats.bpmnFiles(), stats.fhirFiles(),
                stats.missingBpmn(), stats.missingFhir()
        ));

        logger.info(String.format("Plugins Found:   %d", lints.size()));
        logger.info("");
    }

    /**
     * Prints linting results summary.
     */
    private void printLintsResultsSummary(
            Map<String, DsfLinter.PluginLinter> lints,
            LeftoverResourceDetector.AnalysisResult leftoverResults) {

        int totalPluginErrors = lints.values().stream().mapToInt(v -> v.output().getErrorCount()).sum();
        int totalPluginWarnings = lints.values().stream().mapToInt(v -> v.output().getWarningCount()).sum();
        int totalPluginInfos = lints.values().stream().mapToInt(v -> v.output().getInfoCount()).sum();
        int totalPluginSuccess = lints.values().stream().mapToInt(v -> v.output().getSuccessCount()).sum();
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
    private void printExecutionSummary(long executionTime, DsfLinter.Config config) {
        logger.info("");
        logger.info(String.format("Time:            %.2f seconds", executionTime / 1000.0));
        logger.info(String.format("Reports:         %s", config.reportPath().toAbsolutePath()));
        logger.info("=".repeat(80));
    }

    /**
     * Prints the final linting result (SUCCESS or FAILED).
     */
    private void printFinalResult(
            Map<String, DsfLinter.PluginLinter> lints,
            DsfLinter.Config config) {

        int totalPluginErrors = lints.values().stream().mapToInt(v -> v.output().getErrorCount()).sum();

        if (config.failOnErrors() && totalPluginErrors > 0) {
            Console.red("Result: FAILED (" + totalPluginErrors + " plugin errors found)");
        } else {
            Console.green("Result: SUCCESS");
        }

        logger.info("=".repeat(80));
    }
}