package dev.dsf.utils.validator.report;

import dev.dsf.utils.validator.DsfValidatorImpl;
import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.analysis.LeftoverResourceDetector;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.PluginDefinitionProcessPluginRessourceNotLoadedValidationItem;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.service.ResourceDiscoveryService;
import dev.dsf.utils.validator.util.Console;
import dev.dsf.utils.validator.util.api.ApiVersion;
import dev.dsf.utils.validator.util.api.ApiVersionHolder;
import dev.dsf.utils.validator.util.validation.ValidationOutput;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates HTML validation reports and console output with a unified structure.
 * ALWAYS creates subdirectories for each plugin, regardless of count.
 * This ensures consistent report structure for both single and multi-plugin projects.
 */
public class ValidationReportGenerator {

    private final Logger logger;
    private final HtmlReportGenerator htmlGenerator;

    public ValidationReportGenerator(Logger logger) {
        this.logger = logger;
        this.htmlGenerator = new HtmlReportGenerator(logger);
    }

    /**
     * Generates validation reports for all plugins.
     * ALWAYS uses the same structure with plugin subdirectories.
     *
     * @param validations     Map of plugin validations
     * @param discovery       Discovery results
     * @param leftoverResults Leftover resource analysis (may be null)
     * @param config          Validator configuration
     */
    public void generateReports(
            Map<String, DsfValidatorImpl.PluginValidation> validations,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            DsfValidatorImpl.Config config) throws IOException {

        logger.info("Generating validation reports...");

        if (!config.generateHtmlReport()) {
            logger.info("HTML report generation is disabled. Skipping report generation.");
            return;
        }

        Files.createDirectories(config.reportPath());

        // ALWAYS generate individual plugin reports in subdirectories
        for (Map.Entry<String, DsfValidatorImpl.PluginValidation> entry : validations.entrySet()) {
            String pluginName = entry.getKey();
            DsfValidatorImpl.PluginValidation validation = entry.getValue();

            // Create plugin-specific subdirectory
            Path pluginReportDir = config.reportPath().resolve(pluginName);
            Files.createDirectories(pluginReportDir);

            generatePluginReport(pluginName, validation, pluginReportDir, config);
        }

        // Generate master report that aggregates all plugins
        generateMasterReport(validations, discovery, leftoverResults, config);

        logger.info("Reports generated at: " + config.reportPath().toAbsolutePath());
    }

    /**
     * Generates HTML report for a single plugin in its dedicated subdirectory.
     */
    private void generatePluginReport(
            String pluginName,
            DsfValidatorImpl.PluginValidation validation,
            Path pluginReportDir,
            DsfValidatorImpl.Config config) throws IOException {

        logger.debug("Generating report for plugin: " + pluginName);

        Path htmlReportPath = pluginReportDir.resolve("validation.html");
        htmlGenerator.generatePluginReport(pluginName, validation, htmlReportPath);

        logger.debug("Plugin report saved to: " + pluginReportDir);
    }

    /**
     * Generates the master report that shows all plugins.
     * This report always expects plugins to have their own subdirectories.
     */
    private void generateMasterReport(
            Map<String, DsfValidatorImpl.PluginValidation> validations,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            DsfValidatorImpl.Config config) throws IOException {

        logger.debug("Generating master report...");

        Path masterHtmlPath = config.reportPath().resolve("report.html");

        htmlGenerator.generateMasterReport(
                validations,
                discovery,
                leftoverResults,
                masterHtmlPath,
                config
        );

        logger.debug("Master report saved to: " + config.reportPath());
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
        logger.info("");
        logger.info("-".repeat(80));
        Console.bold(String.format("Validating Plugin [%d/%d]: %s", current, total, pluginName));
        logger.info("-".repeat(80));
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

        // LINE 1: ALWAYS show errors
        if (errorCount > 0) {
            System.out.println("\u001B[31m  ✗ Errors:   " + errorCount + "\u001B[0m");
            System.out.flush();
        } else {
            System.out.println("\u001B[32m  ✓ Errors:   " + errorCount + "\u001B[0m");
            System.out.flush();
        }

        // LINE 2: ALWAYS show warnings
        if (warningCount > 0) {
            Console.yellow("  ⚠ Warnings: " + warningCount);
        } else {
            logger.info("  ✓ Warnings: " + warningCount);
        }

        // LINE 3: ALWAYS show infos
        if (infoCount > 0) {
            logger.info("  ℹ Infos:    " + infoCount);
        } else {
            logger.info("  ✓ Infos:    " + infoCount);
        }

        // LINE 4: ALWAYS show success count
        if (successCount > 0) {
            Console.green("  ✓ Success:  " + successCount);
        } else {
            logger.info("  ✓ Success:  " + successCount);
        }

        // SUCCESS items details: Only in verbose mode
        if (logger.isVerbose() && successCount > 0) {
            logger.info("");
            Console.green("  Detailed SUCCESS items (" + successCount + "):");

            output.validationItems().stream()
                    .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                    .forEach(item -> Console.green("    * " + item));
        }
    }

    /**
     * Prints leftover resources found in the project.
     * This provides a separate, clear section for unreferenced resources.
     *
     * @param leftoverItems The list of leftover validation items to display
     */
    public void printLeftoverResources(List<AbstractValidationItem> leftoverItems) {
        if (leftoverItems.isEmpty()) {
            return;
        }

        // Separate leftover warnings from success items
        List<AbstractValidationItem> leftoverWarnings = leftoverItems.stream()
                .filter(item -> item instanceof PluginDefinitionProcessPluginRessourceNotLoadedValidationItem)
                .toList();

        if (leftoverWarnings.isEmpty()) {
            return;
        }

        ValidationOutput leftoverOutput = new ValidationOutput(leftoverWarnings);
        long errorCount = leftoverOutput.getErrorCount();
        long warningCount = leftoverOutput.getWarningCount();
        long infoCount = leftoverOutput.getInfoCount();

        long nonSuccessCount = errorCount + warningCount + infoCount;

        logger.info("Found " + nonSuccessCount + " Leftover Resources issue(s): (" + errorCount + " errors, " + warningCount + " warnings, " + infoCount + " infos)");

        // Print errors if any
        if (errorCount > 0) {
            Console.red("  ✗ ERROR items:");
            leftoverWarnings.stream()
                    .filter(item -> item.getSeverity() == ValidationSeverity.ERROR)
                    .forEach(e -> Console.red("    * " + e));
        }

        // Print warnings
        if (warningCount > 0) {
            Console.yellow("  ⚠ WARN items:");
            leftoverWarnings.stream()
                    .filter(item -> item.getSeverity() == ValidationSeverity.WARN)
                    .forEach(w -> Console.yellow("    * " + w));
        }

        // Print infos
        if (infoCount > 0) {
            logger.info("  ℹ INFO items:");
            leftoverWarnings.stream()
                    .filter(item -> item.getSeverity() == ValidationSeverity.INFO)
                    .forEach(i -> logger.info("    * " + i));
        }

        // Print success items only in verbose mode
        if (logger.isVerbose()) {
            List<AbstractValidationItem> successItems = leftoverItems.stream()
                    .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                    .toList();

            if (!successItems.isEmpty()) {
                logger.info("");
                Console.green("  Additional SUCCESS items (" + successItems.size() + "):");
                successItems.forEach(s -> Console.green("    * " + s));
            }
        }
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

        logger.info("");
        logger.info("=".repeat(80));
        Console.bold("Validation Complete!");
        logger.info("=".repeat(80));
        logger.info("");
        logger.info("--- Project Analysis Summary ---");
        logger.info("");

        var stats = discovery.getStatistics();
        logger.info(String.format(
                "Resources Found: %d BPMN, %d FHIR (%d missing BPMN, %d missing FHIR)",
                stats.bpmnFiles(), stats.fhirFiles(),
                stats.missingBpmn(), stats.missingFhir()
        ));

        logger.info(String.format("Plugins Found:   %d", validations.size()));
        logger.info("");

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

        logger.info("");
        logger.info(String.format("Time:            %.2f seconds", executionTime / 1000.0));
        logger.info(String.format("Reports:         %s", config.reportPath().toAbsolutePath()));
        logger.info("=".repeat(80));

        if (config.failOnErrors() && totalPluginErrors > 0) {
            Console.red("Result: FAILED (" + totalPluginErrors + " plugin errors found)");
        } else {
            Console.green("Result: SUCCESS");
        }

        logger.info("=".repeat(80));
    }

    /**
     * Helper class for generating HTML reports.
     */
    private static class HtmlReportGenerator {
        private final Logger logger;
        private final TemplateEngine templateEngine;

        HtmlReportGenerator(Logger logger) {
            this.logger = logger;
            this.templateEngine = createTemplateEngine();
        }

        private TemplateEngine createTemplateEngine() {
            ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
            resolver.setTemplateMode("HTML");
            resolver.setPrefix("/templates/");
            resolver.setSuffix(".html");
            resolver.setCharacterEncoding("UTF-8");
            TemplateEngine engine = new TemplateEngine();
            engine.setTemplateResolver(resolver);
            return engine;
        }

        void generatePluginReport(
                String pluginName,
                DsfValidatorImpl.PluginValidation validation,
                Path outputPath) throws IOException {

            // Generate plugin-specific HTML
            String html = formatPluginHtml(pluginName, validation);
            Files.writeString(outputPath, html);
            logger.debug("HTML report written to: " + outputPath);
        }

        void generateMasterReport(
                Map<String, DsfValidatorImpl.PluginValidation> validations,
                ResourceDiscoveryService.DiscoveryResult discovery,
                LeftoverResourceDetector.AnalysisResult leftoverResults,
                Path outputPath,
                DsfValidatorImpl.Config config) throws IOException {

            // Generate master HTML
            String html = formatMasterHtml(validations, discovery, leftoverResults, config);
            Files.writeString(outputPath, html);
            logger.debug("Master HTML report written to: " + outputPath);
        }

        private String formatPluginHtml(String pluginName, DsfValidatorImpl.PluginValidation validation) {
            Context context = new Context();

            ApiVersion apiVersion = validation.apiVersion();
            ApiVersionHolder.setVersion(apiVersion);

            getLogos(context);
            context.setVariable("pluginName", pluginName);
            context.setVariable("pluginClass", validation.pluginClass());
            context.setVariable("apiVersion", apiVersion.toString());

            int errorCount = validation.output().getErrorCount();
            int warningCount = validation.output().getWarningCount();
            int infoCount = validation.output().getInfoCount();
            int successCount = validation.output().getSuccessCount();

            context.setVariable("errorCount", errorCount);
            context.setVariable("warningCount", warningCount);
            context.setVariable("infoCount", infoCount);
            context.setVariable("successCount", successCount);
            context.setVariable("hasErrors", errorCount > 0);

            List<AbstractValidationItem> sortedItems = new ArrayList<>(validation.output().validationItems());
            sortedItems.sort(
                    Comparator.comparingInt((AbstractValidationItem i) ->
                                    ValidationOutput.SEVERITY_RANK.getOrDefault(i.getSeverity(), Integer.MAX_VALUE))
                            .thenComparing(AbstractValidationItem::toString)
            );

            Map<String, List<Map<String, Object>>> itemsBySeverity = new LinkedHashMap<>();
            itemsBySeverity.put("ERROR", new ArrayList<>());
            itemsBySeverity.put("WARN", new ArrayList<>());
            itemsBySeverity.put("INFO", new ArrayList<>());
            itemsBySeverity.put("SUCCESS", new ArrayList<>());

            for (AbstractValidationItem item : sortedItems) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("severity", item.getSeverity().toString());

                List<String> getterNames = List.of(
                        "getElementId", "getProcessId", "getDescription", "getBpmnFile",
                        "getFhirReference", "getIssueType", "getResourceId", "getResourceFile",
                        "getFileName", "getLocation", "getMessage"
                );

                for (String getterName : getterNames) {
                    invokeGetter(item, getterName, itemMap);
                }

                itemMap.put("fullMessage", item.toString());

                String severity = item.getSeverity().toString();
                if (itemsBySeverity.containsKey(severity)) {
                    itemsBySeverity.get(severity).add(itemMap);
                }
            }

            context.setVariable("itemsBySeverity", itemsBySeverity);
            context.setVariable("hasItems", !sortedItems.isEmpty());

            ApiVersionHolder.clear();

            return templateEngine.process("single_plugin_report", context);
        }

        private String formatMasterHtml(
                Map<String, DsfValidatorImpl.PluginValidation> validations,
                ResourceDiscoveryService.DiscoveryResult discovery,
                LeftoverResourceDetector.AnalysisResult leftoverResults,
                DsfValidatorImpl.Config config) {

            Context context = new Context();
            String projectName = config.projectPath().getFileName().toString();
            projectName = projectName.replaceFirst("^dsf-validator-", "");
            context.setVariable("projectName", projectName);
            getLogos(context);
            context.setVariable("validations", validations.values().stream().map(v -> {
                Map<String, Object> validationMap = new LinkedHashMap<>();
                validationMap.put("pluginName", v.pluginName());
                validationMap.put("pluginClass", v.pluginClass());
                validationMap.put("apiVersion", v.apiVersion());
                validationMap.put("errors", v.output().getErrorCount());
                validationMap.put("warnings", v.output().getWarningCount());
                validationMap.put("infos", v.output().getInfoCount());
                validationMap.put("htmlReportPath", "./" + v.pluginName() + "/validation.html");
                return validationMap;
            }).collect(Collectors.toList()));

            context.setVariable("totalPlugins", validations.size());
            int totalErrors = validations.values().stream().mapToInt(v -> v.output().getErrorCount()).sum();
            context.setVariable("totalErrors", totalErrors);
            context.setVariable("totalWarnings", validations.values().stream().mapToInt(v -> v.output().getWarningCount()).sum());
            context.setVariable("totalSuccesses", validations.values().stream().mapToInt(v -> v.output().getSuccessCount()).sum());
            context.setVariable("totalInfos", validations.values().stream().mapToInt(v -> v.output().getInfoCount()).sum());

            context.setVariable("hasErrors", totalErrors > 0);

            if (leftoverResults != null) {
                context.setVariable("leftoverAnalysis", leftoverResults);
            }

            return templateEngine.process("summary_report.html", context);
        }

        private void getLogos(Context context) {
            String logoDark = loadLogoAsBase64();
            String logoLight = loadLogoAsBase64();

            context.setVariable("logoBase64Dark", logoDark);
            context.setVariable("logoBase64Light", logoLight);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(new Date());
            context.setVariable("reportTimestamp", timestamp);
        }

        private String loadLogoAsBase64() {
            try (InputStream logoStream = getClass().getResourceAsStream("/templates/logo.svg")) {
                if (logoStream != null) {
                    byte[] logoBytes = logoStream.readAllBytes();
                    return Base64.getEncoder().encodeToString(logoBytes);
                } else {
                    logger.warn("Logo '" + "/templates/logo.svg" + "' not found in templates.");
                    return "";
                }
            } catch (IOException e) {
                logger.error("Error loading logo: " + "/templates/logo.svg", e);
                return "";
            }
        }
    }

    /**
     * Tries to invoke a getter method on the given item and puts the returned value into the map.
     * This helper method encapsulates the repetitive try-catch logic.
     *
     * @param item The object on which to invoke the getter.
     * @param getterName The simple name of the getter method (e.g., "getElementId").
     * @param targetMap The map to which the property should be added.
     */
    private static void invokeGetter(Object item, String getterName, Map<String, Object> targetMap) {
        try {
            Method method = item.getClass().getMethod(getterName);
            Object value = method.invoke(item);
            if (value != null) {
                String key = Character.toLowerCase(getterName.charAt(3)) + getterName.substring(4);
                targetMap.put(key, value);
            }
        } catch (Exception ignored) {

        }
    }
}