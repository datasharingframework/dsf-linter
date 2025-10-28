package dev.dsf.linter.report;

import dev.dsf.linter.DsfValidatorImpl;
import dev.dsf.linter.analysis.LeftoverResourceDetector;
import dev.dsf.linter.output.item.AbstractValidationItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.service.ResourceDiscoveryService;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.validation.ValidationOutput;
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
 * Generates HTML validation reports using Thymeleaf templates.
 * Responsible for creating both individual plugin reports and master summary reports.
 */
public class HtmlReportGenerator {

    private final Logger logger;
    private final TemplateEngine templateEngine;

    public HtmlReportGenerator(Logger logger) {
        this.logger = logger;
        this.templateEngine = createTemplateEngine();
    }

    /**
     * Creates and configures the Thymeleaf template engine.
     */
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

    /**
     * Generates an HTML report for a single plugin.
     *
     * @param pluginName The name of the plugin
     * @param validation The plugin validation result
     * @param outputPath The path where the HTML report should be saved
     */
    public void generatePluginReport(
            String pluginName,
            DsfValidatorImpl.PluginValidation validation,
            Path outputPath) throws IOException {

        logger.debug("Generating HTML report for plugin: " + pluginName);

        String html = formatPluginHtml(pluginName, validation);
        Files.writeString(outputPath, html);

        logger.debug("HTML report written to: " + outputPath);
    }

    /**
     * Generates the master HTML report that aggregates all plugins.
     *
     * @param validations     Map of all plugin validations
     * @param discovery       Resource discovery results
     * @param leftoverResults Leftover analysis results
     * @param outputPath      The path where the master HTML report should be saved
     * @param config          Validator configuration
     */
    public void generateMasterReport(
            Map<String, DsfValidatorImpl.PluginValidation> validations,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            Path outputPath,
            DsfValidatorImpl.Config config) throws IOException {

        logger.debug("Generating master HTML report...");

        String html = formatMasterHtml(validations, discovery, leftoverResults, config);
        Files.writeString(outputPath, html);

        logger.debug("Master HTML report written to: " + outputPath);
    }

    /**
     * Formats the HTML content for a single plugin report.
     */
    private String formatPluginHtml(String pluginName, DsfValidatorImpl.PluginValidation validation) {
        Context context = new Context();

        ApiVersion apiVersion = validation.apiVersion();
        ApiVersionHolder.setVersion(apiVersion);

        addLogosToContext(context);
        addPluginMetadata(context, pluginName, validation, apiVersion);
        addValidationCounts(context, validation);
        addValidationItems(context, validation);

        ApiVersionHolder.clear();

        return templateEngine.process("single_plugin_report", context);
    }

    /**
     * Adds plugin metadata to the Thymeleaf context.
     */
    private void addPluginMetadata(Context context, String pluginName,
                                   DsfValidatorImpl.PluginValidation validation,
                                   ApiVersion apiVersion) {
        context.setVariable("pluginName", pluginName);
        context.setVariable("pluginClass", validation.pluginClass());
        context.setVariable("apiVersion", apiVersion.toString());
    }

    /**
     * Adds validation counts to the Thymeleaf context.
     */
    private void addValidationCounts(Context context, DsfValidatorImpl.PluginValidation validation) {
        int errorCount = validation.output().getErrorCount();
        int warningCount = validation.output().getWarningCount();
        int infoCount = validation.output().getInfoCount();
        int successCount = validation.output().getSuccessCount();

        context.setVariable("errorCount", errorCount);
        context.setVariable("warningCount", warningCount);
        context.setVariable("infoCount", infoCount);
        context.setVariable("successCount", successCount);
        context.setVariable("hasErrors", errorCount > 0);
    }

    /**
     * Adds validation items to the Thymeleaf context, grouped by severity.
     */
    private void addValidationItems(Context context, DsfValidatorImpl.PluginValidation validation) {
        List<AbstractValidationItem> sortedItems = new ArrayList<>(validation.output().validationItems());
        sortedItems.sort(
                Comparator.comparingInt((AbstractValidationItem i) ->
                                ValidationOutput.SEVERITY_RANK.getOrDefault(i.getSeverity(), Integer.MAX_VALUE))
                        .thenComparing(AbstractValidationItem::toString)
        );

        Map<String, List<Map<String, Object>>> itemsBySeverity = groupItemsBySeverity(sortedItems);

        context.setVariable("itemsBySeverity", itemsBySeverity);
        context.setVariable("hasItems", !sortedItems.isEmpty());
    }

    /**
     * Groups validation items by severity and converts them to Maps for template rendering.
     */
    private Map<String, List<Map<String, Object>>> groupItemsBySeverity(List<AbstractValidationItem> sortedItems) {
        Map<String, List<Map<String, Object>>> itemsBySeverity = new LinkedHashMap<>();
        itemsBySeverity.put("ERROR", new ArrayList<>());
        itemsBySeverity.put("WARN", new ArrayList<>());
        itemsBySeverity.put("INFO", new ArrayList<>());
        itemsBySeverity.put("SUCCESS", new ArrayList<>());

        for (AbstractValidationItem item : sortedItems) {
            Map<String, Object> itemMap = convertItemToMap(item);
            String severity = item.getSeverity().toString();
            if (itemsBySeverity.containsKey(severity)) {
                itemsBySeverity.get(severity).add(itemMap);
            }
        }

        return itemsBySeverity;
    }

    /**
     * Converts a validation item to a Map for template rendering.
     */
    private Map<String, Object> convertItemToMap(AbstractValidationItem item) {
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

        return itemMap;
    }

    /**
     * Formats the HTML content for the master report.
     */
    private String formatMasterHtml(
            Map<String, DsfValidatorImpl.PluginValidation> validations,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            DsfValidatorImpl.Config config) {

        Context context = new Context();

        String projectName = extractProjectName(config);
        context.setVariable("projectName", projectName);

        addLogosToContext(context);
        addPluginValidationsToContext(context, validations);
        addTotalCounts(context, validations);

        if (leftoverResults != null) {
            context.setVariable("leftoverAnalysis", leftoverResults);
        }

        return templateEngine.process("summary_report.html", context);
    }

    /**
     * Extracts a clean project name from the project path.
     */
    private String extractProjectName(DsfValidatorImpl.Config config) {
        String projectName = config.projectPath().getFileName().toString();
        return projectName.replaceFirst("^dsf-validator-", "");
    }

    /**
     * Adds plugin validations to the Thymeleaf context.
     */
    private void addPluginValidationsToContext(
            Context context,
            Map<String, DsfValidatorImpl.PluginValidation> validations) {

        List<Map<String, Object>> validationList = validations.values().stream()
                .map(this::convertValidationToMap)
                .collect(Collectors.toList());

        context.setVariable("validations", validationList);
    }

    /**
     * Converts a plugin validation to a Map for template rendering.
     */
    private Map<String, Object> convertValidationToMap(DsfValidatorImpl.PluginValidation validation) {
        Map<String, Object> validationMap = new LinkedHashMap<>();
        validationMap.put("pluginName", validation.pluginName());
        validationMap.put("pluginClass", validation.pluginClass());
        validationMap.put("apiVersion", validation.apiVersion());
        validationMap.put("errors", validation.output().getErrorCount());
        validationMap.put("warnings", validation.output().getWarningCount());
        validationMap.put("infos", validation.output().getInfoCount());
        validationMap.put("htmlReportPath", "./" + validation.pluginName() + "/validation.html");
        return validationMap;
    }

    /**
     * Adds total counts across all plugins to the Thymeleaf context.
     */
    private void addTotalCounts(Context context, Map<String, DsfValidatorImpl.PluginValidation> validations) {
        context.setVariable("totalPlugins", validations.size());

        int totalErrors = validations.values().stream().mapToInt(v -> v.output().getErrorCount()).sum();
        int totalWarnings = validations.values().stream().mapToInt(v -> v.output().getWarningCount()).sum();
        int totalSuccesses = validations.values().stream().mapToInt(v -> v.output().getSuccessCount()).sum();
        int totalInfos = validations.values().stream().mapToInt(v -> v.output().getInfoCount()).sum();

        context.setVariable("totalErrors", totalErrors);
        context.setVariable("totalWarnings", totalWarnings);
        context.setVariable("totalSuccesses", totalSuccesses);
        context.setVariable("totalInfos", totalInfos);
        context.setVariable("hasErrors", totalErrors > 0);
    }

    /**
     * Adds logos and timestamp to the Thymeleaf context.
     */
    private void addLogosToContext(Context context) {
        String logo = loadLogoAsBase64();

        context.setVariable("logoBase64Dark", logo);
        context.setVariable("logoBase64Light", logo);

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(new Date());
        context.setVariable("reportTimestamp", timestamp);
    }

    /**
     * Loads the logo SVG file and encodes it as Base64.
     */
    private String loadLogoAsBase64() {
        try (InputStream logoStream = getClass().getResourceAsStream("/templates/logo.svg")) {
            if (logoStream != null) {
                byte[] logoBytes = logoStream.readAllBytes();
                return Base64.getEncoder().encodeToString(logoBytes);
            } else {
                logger.warn("Logo '/templates/logo.svg' not found in templates.");
                return "";
            }
        } catch (IOException e) {
            logger.error("Error loading logo: /templates/logo.svg", e);
            return "";
        }
    }

    /**
     * Tries to invoke a getter method on the given item and puts the returned value into the map.
     *
     * @param item       The object on which to invoke the getter
     * @param getterName The simple name of the getter method (e.g., "getElementId")
     * @param targetMap  The map to which the property should be added
     */
    private void invokeGetter(Object item, String getterName, Map<String, Object> targetMap) {
        try {
            Method method = item.getClass().getMethod(getterName);
            Object value = method.invoke(item);
            if (value != null) {
                String key = Character.toLowerCase(getterName.charAt(3)) + getterName.substring(4);
                targetMap.put(key, value);
            }
        } catch (Exception ignored) {
            // Getter method not available for this item type, which is expected
        }
    }
}