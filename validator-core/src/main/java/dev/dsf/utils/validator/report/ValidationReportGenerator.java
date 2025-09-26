package dev.dsf.utils.validator.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dsf.utils.validator.DsfValidatorImpl;
import dev.dsf.utils.validator.analysis.LeftoverResourceDetector;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.service.ResourceDiscoveryService;
import dev.dsf.utils.validator.util.api.ApiVersion;
import dev.dsf.utils.validator.util.api.ApiVersionHolder;
import dev.dsf.utils.validator.util.validation.ValidationOutput;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates validation reports with a unified structure.
 * ALWAYS creates subdirectories for each plugin, regardless of count.
 * This ensures consistent report structure for both single and multi-plugin projects.
 */
public class ValidationReportGenerator {

    private final Logger logger;
    private final JsonReportGenerator jsonGenerator;
    private final HtmlReportGenerator htmlGenerator;

    public ValidationReportGenerator(Logger logger) {
        this.logger = logger;
        this.jsonGenerator = new JsonReportGenerator(logger);
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

        // Ensure base report directory exists
        Files.createDirectories(config.reportPath());

        // ALWAYS generate individual plugin reports in subdirectories
        for (Map.Entry<String, DsfValidatorImpl.PluginValidation> entry : validations.entrySet()) {
            String pluginName = entry.getKey();
            DsfValidatorImpl.PluginValidation validation = entry.getValue();

            // Create plugin-specific subdirectory
            Path pluginReportDir = config.reportPath().resolve(pluginName);
            Files.createDirectories(pluginReportDir);

            // Generate plugin-specific reports in its subdirectory
            generatePluginReports(pluginName, validation, pluginReportDir, config);
        }

        // Generate master report that aggregates all plugins
        generateMasterReport(validations, discovery, leftoverResults, config);

        logger.info("Reports generated at: " + config.reportPath().toAbsolutePath());
    }

    /**
     * Generates reports for a single plugin in its dedicated subdirectory.
     */
    private void generatePluginReports(
            String pluginName,
            DsfValidatorImpl.PluginValidation validation,
            Path pluginReportDir,
            DsfValidatorImpl.Config config) throws IOException {

        logger.debug("Generating reports for plugin: " + pluginName);

        // Generate JSON report in plugin subdirectory
        Path jsonReportPath = pluginReportDir.resolve("validation.json");
        jsonGenerator.generatePluginReport(validation, jsonReportPath);

        // Generate HTML report if requested
        if (config.generateHtmlReport()) {
            Path htmlReportPath = pluginReportDir.resolve("validation.html");
            htmlGenerator.generatePluginReport(pluginName, validation, htmlReportPath);
        }

        logger.debug("Plugin reports saved to: " + pluginReportDir);
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

        // Generate master JSON report
        Path masterJsonPath = config.reportPath().resolve("validation.json");
        jsonGenerator.generateMasterReport(validations, leftoverResults, masterJsonPath);

        // Generate master HTML report if requested
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

        logger.debug("Master report saved to: " + config.reportPath());
    }

    /**
     * Helper class for generating JSON reports.
     */
    private static class JsonReportGenerator {
        private final Logger logger;
        private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        JsonReportGenerator(Logger logger) {
            this.logger = logger;
        }

        void generatePluginReport(
                DsfValidatorImpl.PluginValidation validation,
                Path outputPath) throws IOException {

            // Generate plugin-specific JSON
            String json = formatPluginJson(validation);
            Files.writeString(outputPath, json);
            logger.debug("JSON report written to: " + outputPath);
        }

        void generateMasterReport(
                Map<String, DsfValidatorImpl.PluginValidation> validations,
                LeftoverResourceDetector.AnalysisResult leftoverResults,
                Path outputPath) throws IOException {

            // Generate master JSON with all plugins
            String json = formatMasterJson(validations, leftoverResults);
            Files.writeString(outputPath, json);
            logger.debug("Master JSON report written to: " + outputPath);
        }

        private String formatPluginJson(DsfValidatorImpl.PluginValidation validation) throws IOException {
            Map<String, Object> root = new LinkedHashMap<>();
            ApiVersion apiVersion = validation.apiVersion();
            ApiVersionHolder.setVersion(apiVersion); // Set for this thread

            List<AbstractValidationItem> sortedItems = new ArrayList<>(validation.output().validationItems());
            sortedItems.sort(
                    Comparator.comparingInt((AbstractValidationItem i) ->
                                    ValidationOutput.SEVERITY_RANK.getOrDefault(i.getSeverity(), Integer.MAX_VALUE))
                            .thenComparing(AbstractValidationItem::toString)
            );

            root.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            root.put("apiVersion", ApiVersionHolder.getVersion());
            root.put("summary", Map.of(
                    "ERROR", validation.output().getErrorCount(),
                    "WARN", validation.output().getWarningCount(),
                    "INFO", validation.output().getInfoCount(),
                    "SUCCESS", validation.output().getSuccessCount()
            ));
            root.put("validationItems", validation.output().validationItems());

            ApiVersionHolder.clear(); // Clean up
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        }

        private String formatMasterJson(
                Map<String, DsfValidatorImpl.PluginValidation> validations,
                LeftoverResourceDetector.AnalysisResult leftoverResults) throws IOException {

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            root.put("totalPlugins", validations.size());
            root.put("summary", Map.of(
                    "totalErrors", getTotalErrors(validations),
                    "totalWarnings", getTotalWarnings(validations),
                    "leftoverResources", leftoverResults != null ? leftoverResults.getTotalLeftoverCount() : 0
            ));

            if (leftoverResults != null) {
                root.put("leftoverAnalysis", Map.of(
                        "totalLeftovers", leftoverResults.getTotalLeftoverCount(),
                        "bpmnLeftovers", leftoverResults.leftoverBpmnPaths(),
                        "fhirLeftovers", leftoverResults.leftoverFhirPaths()
                ));
            }

            root.put("pluginDetails", validations.entrySet().stream()
                    .map(entry -> Map.of(
                            "name", entry.getKey(),
                            "class", entry.getValue().pluginClass(),
                            "apiVersion", entry.getValue().apiVersion().toString(),
                            "errors", entry.getValue().output().getErrorCount(),
                            "warnings", entry.getValue().output().getWarningCount(),
                            "report", "./" + entry.getKey() + "/validation.json"
                    ))
                    .collect(Collectors.toList()));

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        }

        private int getTotalErrors(Map<String, DsfValidatorImpl.PluginValidation> validations) {
            return validations.values().stream()
                    .mapToInt(v -> v.output().getErrorCount())
                    .sum();
        }

        private int getTotalWarnings(Map<String, DsfValidatorImpl.PluginValidation> validations) {
            return validations.values().stream()
                    .mapToInt(v -> v.output().getWarningCount())
                    .sum();
        }
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
                DsfValidatorImpl.Config config) throws IOException{

            // Generate master HTML
            String html = formatMasterHtml(validations, discovery, leftoverResults, config);
            Files.writeString(outputPath, html);
            logger.debug("Master HTML report written to: " + outputPath);
        }

        private String formatPluginHtml(String pluginName, DsfValidatorImpl.PluginValidation validation) {
            // Plugin-specific HTML implementation
            // This would use a template or build the HTML structure
            return String.format(
                    "<html><body><h1>Plugin: %s</h1><p>Errors: %d, Warnings: %d</p></body></html>",
                    pluginName,
                    validation.output().getErrorCount(),
                    validation.output().getWarningCount()
            );
        }

        private String formatMasterHtml(
                Map<String, DsfValidatorImpl.PluginValidation> validations,
                ResourceDiscoveryService.DiscoveryResult discovery,
                LeftoverResourceDetector.AnalysisResult leftoverResults,
                DsfValidatorImpl.Config config) {

            Context context = new Context();
            context.setVariable("projectName", config.projectPath().getFileName().toString());
            try (InputStream logoStream = getClass().getResourceAsStream("/templates/DSF_Logo_monochrom_w.svg")) {
                if (logoStream != null) {
                    byte[] logoBytes = logoStream.readAllBytes();
                    String logoBase64 = Base64.getEncoder().encodeToString(logoBytes);
                    context.setVariable("logoBase64", logoBase64);
                } else {
                    // Fallback, falls das Logo nicht gefunden wird
                    context.setVariable("logoBase64", "");
                    logger.warn("Logo 'DSF_Logo_monochrom_w.svg' not found in templates.");
                }
            } catch (IOException e) {
                logger.error("Error loading logo for report", e);
                context.setVariable("logoBase64", "");
            }

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(new Date());
            context.setVariable("reportTimestamp", timestamp);
            context.setVariable("validations", validations.values().stream().map(v -> {
                Map<String, Object> validationMap = new LinkedHashMap<>();
                validationMap.put("pluginName", v.pluginName());
                validationMap.put("pluginClass", v.pluginClass());
                validationMap.put("apiVersion", v.apiVersion());
                validationMap.put("errors", v.output().getErrorCount());
                validationMap.put("warnings", v.output().getWarningCount());
                validationMap.put("infos", v.output().getInfoCount());
                validationMap.put("jsonReportPath", "./" + v.pluginName() + "/validation.json");
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

            return templateEngine.process("plugin_report", context); // Assuming 'plugin_report' is the name of your HTML template
        }
    }
}