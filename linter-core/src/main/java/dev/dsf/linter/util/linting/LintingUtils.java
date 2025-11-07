package dev.dsf.linter.util.linting;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.util.resource.ResourceResolutionResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class LintingUtils {

    /**
     * Computes the count of ERROR, WARNING, and INFO severities for a given list of linting items.
     */
    public static class SeverityCount {
        private final long errors;
        private final long warnings;
        private final long infos;
        private final long total;

        public SeverityCount(long errors, long warnings, long infos) {
            this.errors = errors;
            this.warnings = warnings;
            this.infos = infos;
            this.total = errors + warnings + infos;
        }

        public long getErrors() {
            return errors;
        }

        public long getWarnings() {
            return warnings;
        }

        public long getInfos() {
            return infos;
        }

        public long getTotal() {
            return total;
        }
    }

    /**
     * Counts severities (ERROR, WARN, INFO) in the given list of lint items.
     *
     * @param items the list of lint items
     * @return a SeverityCount object containing the counts
     */
    public static SeverityCount countSeverities(List<? extends AbstractLintItem> items) {
        if (items == null || items.isEmpty()) {
            return new SeverityCount(0, 0, 0);
        }

        long errors = items.stream()
                .filter(i -> i.getSeverity() == LinterSeverity.ERROR)
                .count();
        long warnings = items.stream()
                .filter(i -> i.getSeverity() == LinterSeverity.WARN)
                .count();
        long infos = items.stream()
                .filter(i -> i.getSeverity() == LinterSeverity.INFO)
                .count();

        return new SeverityCount(errors, warnings, infos);
    }

    /**
     * Checks if the given string is null or empty (after trimming).
     *
     * @param value the string to check
     * @return {@code true} if the string is null or empty; {@code false} otherwise
     */
    public static boolean isEmpty(String value) {
        return (value == null || value.trim().isEmpty());
    }

    /**
     * Checks if the given string contains a version placeholder.
     * <p>
     * A valid placeholder is expected to be in the format "${someWord}" or "#{someWord}", with at least one character inside.
     * </p>
     *
     * @param rawValue the string to check for a placeholder
     * @return {@code true} if the string contains a valid placeholder; {@code false} otherwise
     */
    public static boolean containsPlaceholder(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return false;
        }
        // Regex explanation:
        // (\\$|#)      : Matches either a '$' or '#' character.
        // "\\{"        : Matches the literal '{'.
        // "[^\\}]+":   : Ensures that at least one character (that is not '}') is present.
        // "\\}"        : Matches the literal '}'.
        // ".*" before and after allows the placeholder to appear anywhere in the string.
        return rawValue.matches(".*(?:\\$|#)\\{[^\\}]+\\}.*");
    }

    /**
     * Attempts to find the project's root directory by traversing up from the given path.
     * <p>
     * Uses multiple detection strategies to support different project layouts:
     * </p>
     * <ol>
     *   <li><strong>Explicit configuration:</strong> Checks for system property {@code dsf.projectRoot}
     *       or environment variable {@code DSF_PROJECT_ROOT}. If either is set and points to a valid
     *       directory, that path is returned.</li>
     *   <li><strong>Maven project:</strong> Looks for {@code pom.xml} file</li>
     *   <li><strong>Maven/Gradle workspace:</strong> Looks for {@code src/} directory</li>
     *   <li><strong>Exploded JAR / CI layout:</strong> Looks for {@code fhir/} directory</li>
     * </ol>
     *
     * <p>
     * This method is used by both BPMN and FHIR linters to locate project resources.
     * The order of strategies ensures compatibility with local development, CI/CD pipelines,
     * and exploded JAR scenarios.
     * </p>
     *
     * @param filePath the path to start searching from (typically a resource file path)
     * @return the project root directory, or the parent of the file as a fallback
     */
    public static File getProjectRoot(Path filePath) {
        if (filePath == null) {
            return new File(".");
        }

        // Strategy 1: Explicit configuration (system property or environment variable)
        String cfg = Optional.ofNullable(System.getProperty("dsf.projectRoot"))
                .orElse(System.getenv("DSF_PROJECT_ROOT"));
        if (cfg != null && !cfg.isBlank()) {
            File dir = new File(cfg);
            if (dir.isDirectory()) {
                return dir;
            }
        }

        // Strategy 2-4: Implicit discovery by traversing up the directory tree
        Path current = filePath.getParent();
        while (current != null) {
            // Strategy 2: Maven project (pom.xml)
            if (Files.exists(current.resolve("pom.xml"))) {
                return current.toFile();
            }

            // Strategy 3: Maven/Gradle workspace (src/ directory)
            if (Files.isDirectory(current.resolve("src"))) {
                return current.toFile();
            }

            // Strategy 4: Exploded JAR / CI layout (fhir/ directory)
            if (Files.isDirectory(current.resolve("fhir"))) {
                return current.toFile();
            }

            current = current.getParent();
        }

        // Fallback: return parent of file
        return filePath.getParent() != null ? filePath.getParent().toFile() : new File(".");
    }


    /**
     * Filters lint items by the specified severity.
     *
     * @param items the list of lint items
     * @param severity the desired {@link LinterSeverity}
     * @return a list containing only items with the given severity
     */
    public static List<AbstractLintItem> filterBySeverity(
            List<AbstractLintItem> items, LinterSeverity severity) {
        if (items == null || severity == null)
            return List.of();

        return items.stream()
                .filter(item -> item.getSeverity() == severity)
                .collect(Collectors.toList());
    }

    /**
     * Filters lint items to include only BpmnLintItem instances.
     *
     * @param items the list of lint items
     * @return a list containing only BPMN lint items
     */
    public static List<AbstractLintItem> onlyBpmnItems(List<AbstractLintItem> items) {
        if (items == null) return List.of();

        return items.stream()
                .filter(item -> item instanceof BpmnLintItem)
                .collect(Collectors.toList());
    }

    /**
     * Filters lint items to include only FhirLintItem instances.
     *
     * @param items the list of lint items
     * @return a list containing only FHIR lint items
     */
    public static List<AbstractLintItem> onlyFhirItems(List<AbstractLintItem> items) {
        if (items == null) return List.of();

        return items.stream()
                .filter(item -> item instanceof FhirLintItem)
                .collect(Collectors.toList());
    }

    /**
     * Filters lint items to include only PluginLintItem instances.
     *
     * @param items the list of lint items
     * @return a list containing only Plugin lint items
     */
    public static List<AbstractLintItem> onlyPluginItems(List<AbstractLintItem> items) {
        if (items == null) return List.of();

        return items.stream()
                .filter(item -> item instanceof PluginLintItem)
                .collect(Collectors.toList());
    }

    /**
     * Creates SUCCESS lint items for resources found in dependency JARs.
     * These items indicate that the resources were successfully resolved from dependencies.
     *
     * @param pluginName the name of the plugin being linted
     * @param dependencyResources map of resources found in dependency JARs
     * @param resourceType the type of resource (e.g., "BPMN" or "FHIR")
     * @return list of SUCCESS lint items for dependency resources
     */
    public static List<AbstractLintItem> createDependencySuccessItems(
            String pluginName,
            Map<String, ResourceResolutionResult> dependencyResources,
            String resourceType) {

        List<AbstractLintItem> items = new ArrayList<>();

        if (dependencyResources == null || dependencyResources.isEmpty()) {
            return items;
        }

        for (Map.Entry<String, ResourceResolutionResult> entry : dependencyResources.entrySet()) {
            String reference = entry.getKey();
            ResourceResolutionResult result = entry.getValue();

            if (result.file().isPresent()) {
                items.add(new PluginDefinitionLintItemSuccess(
                        result.file().get(),
                        pluginName,
                        String.format("%s resource '%s' found in dependency JAR (%s)",
                                resourceType,
                                reference,
                                result.actualLocation())
                ));
            }
        }

        return items;
    }
}