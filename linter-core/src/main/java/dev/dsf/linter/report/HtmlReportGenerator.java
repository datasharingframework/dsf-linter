package dev.dsf.linter.report;

import dev.dsf.linter.DsfLinter;
import dev.dsf.linter.analysis.LeftoverResourceDetector;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.linting.LintingOutput;
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
import java.util.stream.Stream;

/**
 * Generates HTML linter reports using Thymeleaf templates.
 * Responsible for creating both individual plugin reports and master summary reports.
 */
public class HtmlReportGenerator {

    /** Relative path from {@code <reportDir>/<plugin>/lints.html} to the master report. */
    private static final String MASTER_REPORT_HREF = "../report.html";

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
     * @param pluginName  The name of the plugin
     * @param lints       The plugin linting result
     * @param outputPath  The path where the HTML report should be saved
     * @param projectPath The extracted JAR project directory used to locate source files
     */
    public void generatePluginReport(
            String pluginName,
            DsfLinter.PluginLinter lints,
            Path outputPath,
            Path projectPath) throws IOException {

        logger.debug("Generating HTML report for plugin: " + pluginName);

        String html = formatPluginHtml(pluginName, lints, projectPath);
        Files.writeString(outputPath, html);

        logger.debug("HTML report written to: " + outputPath);
    }

    /**
     * Generates the master HTML report that aggregates all plugins.
     *
     * @param lints     Map of all plugin lints
     * @param leftoverResults Leftover analysis results
     * @param outputPath      The path where the master HTML report should be saved
     * @param config          Linter configuration
     */
    public void generateMasterReport(
            Map<String, DsfLinter.PluginLinter> lints,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            Path outputPath,
            DsfLinter.Config config) throws IOException {

        logger.debug("Generating master HTML report...");

        String html = formatMasterHtml(lints, leftoverResults, config);
        Files.writeString(outputPath, html);

        logger.debug("Master HTML report written to: " + outputPath);
    }

    /**
     * Formats the HTML content for a single plugin report.
     */
    private String formatPluginHtml(String pluginName, DsfLinter.PluginLinter lints, Path projectPath) {
        Context context = new Context();

        ApiVersion apiVersion = lints.apiVersion();
        ApiVersionHolder.setVersion(apiVersion);

        addLogosToContext(context);
        addPluginMetadata(context, pluginName, lints, apiVersion);
        addLintsCounts(context, lints);
        addLintingItems(context, lints, projectPath);

        ApiVersionHolder.clear();

        return templateEngine.process("single_plugin_report", context);
    }

    /**
     * Adds plugin metadata to the Thymeleaf context.
     */
    private void addPluginMetadata(Context context, String pluginName,
                                   DsfLinter.PluginLinter lints,
                                   ApiVersion apiVersion) {
        context.setVariable("pluginName", pluginName);
        context.setVariable("pluginClass", lints.pluginClass());
        context.setVariable("apiVersion", apiVersion.toString());
        context.setVariable("masterReportHref", MASTER_REPORT_HREF);
    }

    /**
     * Adds lints counts to the Thymeleaf context.
     */
    private void addLintsCounts(Context context, DsfLinter.PluginLinter lints) {
        int errorCount = lints.output().getErrorCount();
        int warningCount = lints.output().getWarningCount();
        int infoCount = lints.output().getInfoCount();
        int successCount = lints.output().getSuccessCount();

        context.setVariable("errorCount", errorCount);
        context.setVariable("warningCount", warningCount);
        context.setVariable("infoCount", infoCount);
        context.setVariable("successCount", successCount);
        context.setVariable("hasErrors", errorCount > 0);
    }

    /**
     * Adds lints items to the Thymeleaf context, grouped by severity.
     * Also resolves referenced file contents for the in-report file viewer.
     * After building the content map, removes {@code sourceFile} from any item
     * whose file could not be loaded — keeping the clickable hint only when
     * content is actually available.
     */
    private void addLintingItems(Context context, DsfLinter.PluginLinter lints, Path projectPath) {
        List<AbstractLintItem> sortedItems = new ArrayList<>(lints.output().LintItems());
        sortedItems.sort(
                Comparator.comparingInt((AbstractLintItem i) ->
                                LintingOutput.SEVERITY_RANK.getOrDefault(i.getSeverity(), Integer.MAX_VALUE))
                        .thenComparing(AbstractLintItem::toString)
        );

        Map<String, List<Map<String, Object>>> itemsBySeverity = groupItemsBySeverity(sortedItems);
        Map<String, String> fileContents = buildFileContentMap(sortedItems, projectPath);

        // Strip sourceFile from items whose file content could not be loaded,
        // so no clickable indicator appears for items without a viewable source.
        itemsBySeverity.values().forEach(items ->
                items.forEach(itemMap -> {
                    Object sf = itemMap.get("sourceFile");
                    if (sf != null && !fileContents.containsKey(sf.toString())) {
                        itemMap.remove("sourceFile");
                    }
                })
        );

        context.setVariable("itemsBySeverity", itemsBySeverity);
        context.setVariable("hasItems", !sortedItems.isEmpty());
        context.setVariable("fileContents", fileContents);
    }

    /**
     * Groups lint items by severity and converts them to Maps for template rendering.
     */
    private Map<String, List<Map<String, Object>>> groupItemsBySeverity(List<AbstractLintItem> sortedItems) {
        Map<String, List<Map<String, Object>>> itemsBySeverity = new LinkedHashMap<>();
        itemsBySeverity.put("ERROR", new ArrayList<>());
        itemsBySeverity.put("WARN", new ArrayList<>());
        itemsBySeverity.put("INFO", new ArrayList<>());
        itemsBySeverity.put("SUCCESS", new ArrayList<>());

        for (AbstractLintItem item : sortedItems) {
            Map<String, Object> itemMap = convertItemToMap(item);
            String severity = item.getSeverity().toString();
            if (itemsBySeverity.containsKey(severity)) {
                itemsBySeverity.get(severity).add(itemMap);
            }
        }

        return itemsBySeverity;
    }

    /**
     * Converts a lint item to a Map for template rendering.
     * Adds a {@code highlightTarget} entry used by the in-report file viewer
     * to scroll to and highlight the relevant element.
     */
    private Map<String, Object> convertItemToMap(AbstractLintItem item) {
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

        String highlightTarget = determineHighlightTarget(itemMap);
        if (highlightTarget != null) {
            itemMap.put("highlightTarget", highlightTarget);
        }

        // Pre-compute sourceFile so the template avoids unsupported OGNL Elvis (?:) syntax
        Object resourceFile = itemMap.get("resourceFile");
        Object bpmnFile = itemMap.get("bpmnFile");
        Object fileName = itemMap.get("fileName");
        String sourceFile = resourceFile != null ? resourceFile.toString()
                : bpmnFile != null ? bpmnFile.toString()
                : fileName != null ? fileName.toString()
                : null;
        if (sourceFile != null) {
            itemMap.put("sourceFile", sourceFile);
        }

        return itemMap;
    }

    /**
     * Determines the best string to highlight in the file viewer for a given lint item.
     * <ul>
     *   <li>BPMN: searches for {@code id="<elementId>"} — matches the element definition
     *       precisely, avoiding false hits on {@code sourceRef} / {@code targetRef} etc.</li>
     *   <li>FHIR: uses the full canonical {@code fhirReference} URL (version suffix stripped)
     *       which appears verbatim as an attribute value at the affected location.</li>
     *   <li>Fallback: {@code resourceId} for cases where neither field is available.</li>
     * </ul>
     */
    private String determineHighlightTarget(Map<String, Object> itemMap) {
        // BPMN: search for the XML attribute declaration, not the bare ID string
        Object elementId = itemMap.get("elementId");
        if (elementId != null && !elementId.toString().isBlank()) {
            return "id=\"" + elementId + "\"";
        }

        // FHIR: use the full canonical URL (strip |version suffix if present)
        Object fhirReference = itemMap.get("fhirReference");
        if (fhirReference != null && !fhirReference.toString().isBlank()) {
            String ref = fhirReference.toString();
            int pipeIdx = ref.indexOf('|');
            if (pipeIdx > 0) ref = ref.substring(0, pipeIdx);
            if (!ref.isBlank()) return ref;
        }

        // Fallback: resourceId
        Object resourceId = itemMap.get("resourceId");
        if (resourceId != null && !resourceId.toString().isBlank()) {
            return resourceId.toString();
        }
        return null;
    }

    /**
     * Reads the content of all files referenced by the given lint items.
     * Searches for files by name recursively inside {@code projectPath}.
     *
     * @param items       the lint items whose file references should be resolved
     * @param projectPath root directory of the extracted JAR
     * @return map from file name to file content (raw text)
     */
    private Map<String, String> buildFileContentMap(List<AbstractLintItem> items, Path projectPath) {
        Map<String, String> contentMap = new LinkedHashMap<>();
        if (projectPath == null || !Files.exists(projectPath)) {
            return contentMap;
        }

        Set<String> fileNames = new LinkedHashSet<>();
        for (AbstractLintItem item : items) {
            collectFileName(item, "getResourceFile", fileNames);
            collectFileName(item, "getBpmnFile", fileNames);
            collectFileName(item, "getFileName", fileNames);
        }

        for (String fileName : fileNames) {
            if (fileName == null || fileName.isBlank()) continue;
            try {
                Optional<Path> found = findFileByName(projectPath, fileName);
                if (found.isPresent()) {
                    contentMap.put(fileName, Files.readString(found.get()));
                } else {
                    logger.debug("Referenced file not found in project directory: " + fileName);
                }
            } catch (IOException e) {
                logger.debug("Could not read referenced file '" + fileName + "': " + e.getMessage());
            }
        }

        return contentMap;
    }

    /**
     * Invokes a getter on the item and, if it returns a non-blank String, adds it to the set.
     */
    private void collectFileName(AbstractLintItem item, String getterName, Set<String> fileNames) {
        try {
            Method method = item.getClass().getMethod(getterName);
            Object value = method.invoke(item);
            if (value instanceof String s && !s.isBlank()) {
                fileNames.add(s);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Recursively searches {@code baseDir} for a file whose name matches {@code fileName}.
     */
    private Optional<Path> findFileByName(Path baseDir, String fileName) throws IOException {
        try (Stream<Path> walk = Files.walk(baseDir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst();
        }
    }

    /**
     * Formats the HTML content for the master report.
     */
    private String formatMasterHtml(
            Map<String, DsfLinter.PluginLinter> lints,
            LeftoverResourceDetector.AnalysisResult leftoverResults,
            DsfLinter.Config config) {

        Context context = new Context();

        String projectName = extractProjectName(config);
        context.setVariable("projectName", projectName);

        addLogosToContext(context);
        addPluginLintsToContext(context, lints);
        addTotalCounts(context, lints);

        if (leftoverResults != null) {
            context.setVariable("leftoverAnalysis", leftoverResults);
        }

        return templateEngine.process("summary_report.html", context);
    }

    /**
     * Extracts a clean project name from the project path.
     */
    private String extractProjectName(DsfLinter.Config config) {
        String projectName = config.projectPath().getFileName().toString();
        return projectName.replaceFirst("^dsf-lint-", "");
    }

    /**
     * Adds plugin lints to the Thymeleaf context.
     */
    private void addPluginLintsToContext(
            Context context,
            Map<String, DsfLinter.PluginLinter> lints) {

        List<Map<String, Object>> lintsList = lints.values().stream()
                .map(this::convertLintsToMap)
                .collect(Collectors.toList());

        context.setVariable("lints", lintsList);
    }

    /**
     * Converts a plugin lints to a Map for template rendering.
     */
    private Map<String, Object> convertLintsToMap(DsfLinter.PluginLinter lints) {
        Map<String, Object> lintMap = new LinkedHashMap<>();
        lintMap.put("pluginName", lints.pluginName());
        lintMap.put("pluginClass", lints.pluginClass());
        lintMap.put("apiVersion", lints.apiVersion());
        lintMap.put("errors", lints.output().getErrorCount());
        lintMap.put("warnings", lints.output().getWarningCount());
        lintMap.put("infos", lints.output().getInfoCount());
        lintMap.put("htmlReportPath", "./" + lints.pluginName() + "/lints.html");
        return lintMap;
    }

    /**
     * Adds total counts across all plugins to the Thymeleaf context.
     */
    private void addTotalCounts(Context context, Map<String, DsfLinter.PluginLinter> lints) {
        context.setVariable("totalPlugins", lints.size());

        int totalErrors = lints.values().stream().mapToInt(v -> v.output().getErrorCount()).sum();
        int totalWarnings = lints.values().stream().mapToInt(v -> v.output().getWarningCount()).sum();
        int totalSuccesses = lints.values().stream().mapToInt(v -> v.output().getSuccessCount()).sum();
        int totalInfos = lints.values().stream().mapToInt(v -> v.output().getInfoCount()).sum();

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