package dev.dsf.linter.report;

import dev.dsf.linter.DsfLinter;
import dev.dsf.linter.analysis.LeftoverResourceDetector;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.service.LintingResult;
import dev.dsf.linter.service.ResourceDiscoveryService;
import dev.dsf.linter.util.linting.LintingOutput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Coordinates linter report generation.
 * Delegates console output to LintConsolePrinter, HTML generation to HtmlReportGenerator,
 * and JSON generation to JsonReportGenerator.
 * This class acts as a facade, orchestrating the work of its collaborators.
 */
public class LintingReportGenerator {

    private final Logger logger;
    private final LintConsolePrinter consolePrinter;
    private final HtmlReportGenerator htmlGenerator;
    private final JsonReportGenerator jsonGenerator;

    public LintingReportGenerator(Logger logger) {
        this.logger = logger;
        this.consolePrinter = new LintConsolePrinter(logger);
        this.htmlGenerator = new HtmlReportGenerator(logger);
        this.jsonGenerator = new JsonReportGenerator(logger);
    }

    /**
     * Generates linter reports for all plugins.
     * Creates HTML reports, JSON reports (if enabled), and console output.
     *
     * @param pluginLinter     Map of plugin linting
     * @param discovery        Discovery results
     * @param leftoverResults  Leftover resource analysis (may be null)
     * @param config           Linter configuration
     */
    public void generateReports(
            Map<String, DsfLinter.PluginLinter> pluginLinter,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            DsfLinter.Config config) throws IOException {

        logger.info("Generating lint reports...");

        // Check if any reports are enabled
        if (!config.generateHtmlReport() && !config.generateJsonReport()) {
            logger.info("HTML and JSON report generation are both disabled. Skipping report generation.");
            return;
        }

        Files.createDirectories(config.reportPath());

        generateIndividualPluginReports(pluginLinter, config);
        generateMasterReports(pluginLinter, discovery, leftoverResults, config);

        logger.info("Reports generated at: " + config.reportPath().toAbsolutePath());
    }

    /**
     * Generates individual reports (HTML and/or JSON) for each plugin.
     */
    private void generateIndividualPluginReports(
            Map<String, DsfLinter.PluginLinter> validations,
            DsfLinter.Config config) throws IOException {

        for (Map.Entry<String, DsfLinter.PluginLinter> entry : validations.entrySet()) {
            String pluginName = entry.getKey();
            DsfLinter.PluginLinter validation = entry.getValue();

            Path pluginReportDir = config.reportPath().resolve(pluginName);
            Files.createDirectories(pluginReportDir);

            // Generate HTML report if enabled
            if (config.generateHtmlReport()) {
                Path htmlReportPath = pluginReportDir.resolve("lints.html");
                htmlGenerator.generatePluginReport(pluginName, validation, htmlReportPath);
            }

            // Generate JSON report if enabled
            if (config.generateJsonReport()) {
                Path jsonReportPath = pluginReportDir.resolve("lints.json");
                jsonGenerator.generatePluginReport(pluginName, validation, jsonReportPath);
            }

            logger.debug("Plugin reports saved to: " + pluginReportDir);
        }
    }

    /**
     * Generates the master reports (HTML and/or JSON) that aggregate all plugins.
     */
    private void generateMasterReports(
            Map<String, DsfLinter.PluginLinter> validations,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            DsfLinter.Config config) throws IOException {

        // Generate HTML master report if enabled
        if (config.generateHtmlReport()) {
            Path masterHtmlPath = config.reportPath().resolve("report.html");
            htmlGenerator.generateMasterReport(
                    validations,
                    discovery,
                    leftoverResults,
                    masterHtmlPath,
                    config
            );
        }

        // Generate JSON master report if enabled
        if (config.generateJsonReport()) {
            Path masterJsonPath = config.reportPath().resolve("report.json");
            jsonGenerator.generateMasterReport(
                    validations,
                    discovery,
                    leftoverResults,
                    masterJsonPath,
                    config
            );
        }

        logger.debug("Master reports saved to: " + config.reportPath());
    }

    /**
     * Prints the validation header with project information.
     *
     * @param config The validator configuration
     */
    public void printHeader(DsfLinter.Config config) {
        consolePrinter.printHeader(config);
    }

    /**
     * Prints a phase header for major validation steps.
     *
     * @param phaseName The name of the phase
     */
    public void printPhaseHeader(String phaseName) {
        consolePrinter.printPhaseHeader(phaseName);
    }

    /**
     * Prints a header for individual plugin validation.
     *
     * @param pluginName The name of the plugin
     * @param current    Current plugin number
     * @param total      Total number of plugins
     */
    public void printPluginHeader(String pluginName, int current, int total) {
        consolePrinter.printPluginHeader(pluginName, current, total);
    }

    /**
     * Prints validation sections in fixed order: BPMN → FHIR → Plugin.
     *
     * @param bpmnNonSuccess   Non-SUCCESS BPMN items
     * @param fhirNonSuccess   Non-SUCCESS FHIR items
     * @param pluginNonSuccess Non-SUCCESS Plugin items
     * @param pluginResult     Plugin linting result for ServiceLoader check
     * @param pluginNameShort  Short name of the plugin
     */
    public void printLintSections(
            List<AbstractLintItem> bpmnNonSuccess,
            List<AbstractLintItem> fhirNonSuccess,
            List<AbstractLintItem> pluginNonSuccess,
            LintingResult pluginResult,
            String pluginNameShort){

        consolePrinter.printLintsSections(
                bpmnNonSuccess,
                fhirNonSuccess,
                pluginNonSuccess,
                pluginResult,
                pluginNameShort
        );
    }

    /**
     * Prints a summary of the validation results for a single plugin.
     *
     * @param output The validation output to summarize
     */
    public void printPluginSummary(LintingOutput output) {
        consolePrinter.printPluginSummary(output);
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
            Map<String, DsfLinter.PluginLinter> validations,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            long executionTime,
            DsfLinter.Config config) {

        consolePrinter.printSummary(validations, discovery, leftoverResults, executionTime, config);
    }
}