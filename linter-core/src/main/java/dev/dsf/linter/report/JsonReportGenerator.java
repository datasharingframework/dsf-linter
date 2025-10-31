package dev.dsf.linter.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dsf.linter.DsfLinter;
import dev.dsf.linter.analysis.LeftoverResourceDetector;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.service.ResourceDiscoveryService;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates JSON linter reports using Jackson.
 * Creates structured JSON output for both individual plugin reports and master summary reports.
 */
public class JsonReportGenerator {

    private final Logger logger;
    private final ObjectMapper objectMapper;

    public JsonReportGenerator(Logger logger) {
        this.logger = logger;
        this.objectMapper = createObjectMapper();
    }

    /**
     * Creates and configures the Jackson ObjectMapper for JSON serialization.
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }

    /**
     * Generates a JSON report for a single plugin.
     *
     * @param pluginName The name of the plugin
     * @param lints      The plugin linting result
     * @param outputPath The path where the JSON report should be saved
     */
    public void generatePluginReport(
            String pluginName,
            DsfLinter.PluginLinter lints,
            Path outputPath) throws IOException {

        logger.debug("Generating JSON report for plugin: " + pluginName);

        ApiVersion apiVersion = lints.apiVersion();
        ApiVersionHolder.setVersion(apiVersion);

        try {
            PluginReport report = createPluginReport(pluginName, lints);
            objectMapper.writeValue(outputPath.toFile(), report);
            logger.debug("JSON report written to: " + outputPath);
        } finally {
            ApiVersionHolder.clear();
        }
    }

    /**
     * Generates the master JSON report that aggregates all plugins.
     *
     * @param lints           Map of all plugin lints
     * @param discovery       Resource discovery results
     * @param leftoverResults Leftover analysis results
     * @param outputPath      The path where the master JSON report should be saved
     * @param config          Linter configuration
     */
    public void generateMasterReport(
            Map<String, DsfLinter.PluginLinter> lints,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            Path outputPath,
            DsfLinter.Config config) throws IOException {

        logger.debug("Generating master JSON report...");

        MasterReport report = createMasterReport(lints, discovery, leftoverResults, config);
        objectMapper.writeValue(outputPath.toFile(), report);

        logger.debug("Master JSON report written to: " + outputPath);
    }

    /**
     * Creates a plugin report data structure.
     */
    private PluginReport createPluginReport(String pluginName, DsfLinter.PluginLinter lints) {
        PluginReport report = new PluginReport();
        report.pluginName = pluginName;
        report.pluginClass = lints.pluginClass();
        report.apiVersion = lints.apiVersion().toString();
        report.timestamp = Instant.now().toString();

        // Summary counts
        report.summary = new ReportSummary();
        report.summary.errorCount = lints.output().getErrorCount();
        report.summary.warningCount = lints.output().getWarningCount();
        report.summary.infoCount = lints.output().getInfoCount();
        report.summary.successCount = lints.output().getSuccessCount();
        report.summary.totalItems = lints.output().LintItems().size();

        // Group items by severity
        report.items = groupItemsBySeverity(lints.output().LintItems());

        return report;
    }

    /**
     * Creates a master report data structure.
     */
    private MasterReport createMasterReport(
            Map<String, DsfLinter.PluginLinter> lints,
            ResourceDiscoveryService.DiscoveryResult discovery,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            DsfLinter.Config config) {

        MasterReport report = new MasterReport();
        report.projectName = config.projectPath().getFileName().toString();
        report.projectPath = config.projectPath().toString();
        report.timestamp = Instant.now().toString();

        // Overall summary
        report.summary = new MasterSummary();
        report.summary.totalPlugins = lints.size();
        report.summary.totalErrors = lints.values().stream()
                .mapToInt(v -> v.output().getErrorCount()).sum();
        report.summary.totalWarnings = lints.values().stream()
                .mapToInt(v -> v.output().getWarningCount()).sum();
        report.summary.totalInfos = lints.values().stream()
                .mapToInt(v -> v.output().getInfoCount()).sum();
        report.summary.totalSuccesses = lints.values().stream()
                .mapToInt(v -> v.output().getSuccessCount()).sum();

        // Resource discovery statistics
        var stats = discovery.getStatistics();
        report.discovery = new DiscoveryInfo();
        report.discovery.bpmnFilesFound = stats.bpmnFiles();
        report.discovery.fhirFilesFound = stats.fhirFiles();
        report.discovery.missingBpmn = stats.missingBpmn();
        report.discovery.missingFhir = stats.missingFhir();

        // Leftover analysis
        if (leftoverResults != null && leftoverResults.hasLeftovers()) {
            report.leftoverAnalysis = new LeftoverAnalysis();
            report.leftoverAnalysis.totalLeftovers = leftoverResults.getTotalLeftoverCount();
            report.leftoverAnalysis.leftoverBpmn = new ArrayList<>(leftoverResults.leftoverBpmnPaths());
            report.leftoverAnalysis.leftoverFhir = new ArrayList<>(leftoverResults.leftoverFhirPaths());
        }

        // Plugin details
        report.plugins = lints.entrySet().stream()
                .map(entry -> createPluginSummary(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return report;
    }

    /**
     * Creates a summary for a single plugin in the master report.
     */
    private PluginSummary createPluginSummary(String pluginName, DsfLinter.PluginLinter lints) {
        PluginSummary summary = new PluginSummary();
        summary.pluginName = pluginName;
        summary.pluginClass = lints.pluginClass();
        summary.apiVersion = lints.apiVersion().toString();
        summary.errorCount = lints.output().getErrorCount();
        summary.warningCount = lints.output().getWarningCount();
        summary.infoCount = lints.output().getInfoCount();
        summary.successCount = lints.output().getSuccessCount();
        summary.reportPath = "./" + pluginName + "/lints.json";
        return summary;
    }

    /**
     * Groups lint items by severity and converts them to serializable maps.
     */
    private Map<String, List<Map<String, Object>>> groupItemsBySeverity(List<AbstractLintItem> items) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        grouped.put("ERROR", new ArrayList<>());
        grouped.put("WARN", new ArrayList<>());
        grouped.put("INFO", new ArrayList<>());
        grouped.put("SUCCESS", new ArrayList<>());

        for (AbstractLintItem item : items) {
            Map<String, Object> itemMap = convertItemToMap(item);
            String severity = item.getSeverity().toString();
            if (grouped.containsKey(severity)) {
                grouped.get(severity).add(itemMap);
            }
        }

        return grouped;
    }

    /**
     * Converts a lint item to a Map for JSON serialization.
     * Extracts all available properties using reflection.
     */
    private Map<String, Object> convertItemToMap(AbstractLintItem item) {
        Map<String, Object> itemMap = new LinkedHashMap<>();

        itemMap.put("severity", item.getSeverity().toString());
        itemMap.put("type", item.getClass().getSimpleName());

        List<String> getterNames = Arrays.asList(
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
     * Invokes a getter method on an item and stores the result in the map.
     */
    private void invokeGetter(Object item, String getterName, Map<String, Object> targetMap) {
        try {
            var method = item.getClass().getMethod(getterName);
            Object value = method.invoke(item);
            if (value != null) {
                String key = Character.toLowerCase(getterName.charAt(3)) + getterName.substring(4);
                targetMap.put(key, value.toString());
            }
        } catch (Exception ignored) {
            // Getter not available for this item type - expected
        }
    }

    // JSON Data Structures

    /**
     * Report structure for a single plugin.
     */
    public static class PluginReport {
        public String pluginName;
        public String pluginClass;
        public String apiVersion;
        public String timestamp;
        public ReportSummary summary;
        public Map<String, List<Map<String, Object>>> items;
    }

    /**
     * Summary statistics for a plugin report.
     */
    public static class ReportSummary {
        public int errorCount;
        public int warningCount;
        public int infoCount;
        public int successCount;
        public int totalItems;
    }

    /**
     * Master report structure aggregating all plugins.
     */
    public static class MasterReport {
        public String projectName;
        public String projectPath;
        public String timestamp;
        public MasterSummary summary;
        public DiscoveryInfo discovery;
        public LeftoverAnalysis leftoverAnalysis;
        public List<PluginSummary> plugins;
    }

    /**
     * Overall summary statistics for the master report.
     */
    public static class MasterSummary {
        public int totalPlugins;
        public int totalErrors;
        public int totalWarnings;
        public int totalInfos;
        public int totalSuccesses;
    }

    /**
     * Resource discovery information.
     */
    public static class DiscoveryInfo {
        public int bpmnFilesFound;
        public int fhirFilesFound;
        public int missingBpmn;
        public int missingFhir;
    }

    /**
     * Leftover resource analysis information.
     */
    public static class LeftoverAnalysis {
        public int totalLeftovers;
        public List<String> leftoverBpmn;
        public List<String> leftoverFhir;
    }

    /**
     * Plugin summary for the master report.
     */
    public static class PluginSummary {
        public String pluginName;
        public String pluginClass;
        public String apiVersion;
        public int errorCount;
        public int warningCount;
        public int infoCount;
        public int successCount;
        public String reportPath;
    }
}