package dev.dsf.utils.validator.util.validation;

import dev.dsf.utils.validator.ValidationSeverity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;
import dev.dsf.utils.validator.util.api.ApiVersion;
import dev.dsf.utils.validator.util.api.ApiVersionHolder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <h2>DSF BPMN Validation Output</h2>
 *
 * <p>
 * The {@code ValidationOutput} class encapsulates the results of BPMN or FHIR validation runs.
 * It stores a list of {@link AbstractValidationItem} instances and provides methods to:
 * <ul>
 *   <li>Print issues to the console</li>
 *   <li>Export results as a pretty-printed JSON file with timestamp, API version, and summary</li>
 *   <li>Retrieve associated process identifiers from BPMN validation items</li>
 *   <li>Create empty validation outputs for cases with no issues</li>
 *   <li>Count validation items by severity level</li>
 * </ul>
 * </p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li><b>Immutable Design:</b> Implemented as a Java record for thread safety</li>
 *   <li><b>Flexible Output:</b> Console printing and JSON file export</li>
 *   <li><b>Smart Sorting:</b> Issues sorted by severity and description</li>
 *   <li><b>API Versioning:</b> Includes API version information in JSON output</li>
 *   <li><b>Summary Statistics:</b> Provides counts by validation severity</li>
 * </ul>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Create validation output with items
 * List<AbstractValidationItem> items = validator.validate(bpmnFile);
 * ValidationOutput output = new ValidationOutput(items);
 *
 * // Print to console
 * output.printResults();
 *
 * // Export to JSON file
 * output.writeResultsAsJson(new File("validation-report.json"));
 *
 * // Get process ID from BPMN items
 * String processId = output.getProcessId();
 *
 * // Get counts by severity
 * int errors = output.getErrorCount();
 * int warnings = output.getWarningCount();
 *
 * // Check for issues
 * if (output.hasErrors()) {
 *     System.err.println("Validation failed with errors");
 * }
 *
 * // Create empty output for no issues
 * ValidationOutput empty = ValidationOutput.empty();
 * }</pre>
 *
 * <h3>JSON Output Format:</h3>
 * <pre>
 * {
 *   "timestamp": "2025-04-11 16:58:23",
 *   "apiVersion": "v2",
 *   "summary": {
 *     "ERROR": 2,
 *     "WARN": 1,
 *     "INFO": 0,
 *     "SUCCESS": 5
 *   },
 *   "validationItems": [
 *     { "severity": "ERROR", "message": "...", "location": "..." },
 *     { "severity": "WARN", "message": "...", "location": "..." }
 *   ]
 * }
 * </pre>
 * <p>
 * The JSON output includes:
 * <ul>
 *   <li><b>timestamp:</b> Report generation time in {@code yyyy-MM-dd HH:mm:ss} format</li>
 *   <li><b>apiVersion:</b> Current API version (v1, v2, or unknown)</li>
 *   <li><b>summary:</b> Count of issues by severity level</li>
 *   <li><b>validationItems:</b> Sorted list of validation issues</li>
 * </ul>
 * </p>
 *
 * @param validationItems the list of validation items found during validation
 *
 * @see AbstractValidationItem
 * @see BpmnElementValidationItem
 * @see ValidationSeverity
 */
public record ValidationOutput(List<AbstractValidationItem> validationItems) {

    /**
     * Constructs a {@code ValidationOutput} with the given list of validation items.
     * <p>
     * This compact constructor is automatically generated for the record and ensures
     * that the provided list is stored as-is. The list should contain all validation
     * items discovered during the validation process.
     * </p>
     *
     * @param validationItems the list of validation items to be stored; may be empty but should not be null
     */
    public ValidationOutput {
    }

    /**
     * Returns the validation items stored in this output.
     * <p>
     * This is the accessor method for the record component. The returned list
     * contains all validation items that were provided during construction.
     * </p>
     *
     * @return an unmodifiable view of the validation items list
     */
    @Override
    public List<AbstractValidationItem> validationItems() {
        return validationItems;
    }

    /**
     * Returns the count of validation items with ERROR severity.
     * <p>
     * This method filters the validation items and counts only those with
     * {@link ValidationSeverity#ERROR} severity level.
     * </p>
     *
     * @return the number of error items in the validation output
     */
    public int getErrorCount() {
        return (int) validationItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.ERROR)
                .count();
    }

    /**
     * Returns the count of validation items with WARNING severity.
     * <p>
     * This method filters the validation items and counts only those with
     * {@link ValidationSeverity#WARN} severity level.
     * </p>
     *
     * @return the number of warning items in the validation output
     */
    public int getWarningCount() {
        return (int) validationItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.WARN)
                .count();
    }

    /**
     * Returns the count of validation items with INFO severity.
     * <p>
     * This method filters the validation items and counts only those with
     * {@link ValidationSeverity#INFO} severity level.
     * </p>
     *
     * @return the number of info items in the validation output
     */
    public int getInfoCount() {
        return (int) validationItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.INFO)
                .count();
    }

    /**
     * Returns the count of validation items with SUCCESS severity.
     * <p>
     * This method filters the validation items and counts only those with
     * {@link ValidationSeverity#SUCCESS} severity level.
     * </p>
     *
     * @return the number of success items in the validation output
     */
    public int getSuccessCount() {
        return (int) validationItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                .count();
    }

    /**
     * Checks if there are any validation items with ERROR severity.
     * <p>
     * This is a convenience method that checks whether the validation output
     * contains any errors that would indicate validation failure.
     * </p>
     *
     * @return {@code true} if there are any error items, {@code false} otherwise
     */
    public boolean hasErrors() {
        return validationItems.stream()
                .anyMatch(item -> item.getSeverity() == ValidationSeverity.ERROR);
    }

    /**
     * Checks if there are any validation items with WARNING severity.
     * <p>
     * This is a convenience method that checks whether the validation output
     * contains any warnings that might need attention.
     * </p>
     *
     * @return {@code true} if there are any warning items, {@code false} otherwise
     */
    public boolean hasWarnings() {
        return validationItems.stream()
                .anyMatch(item -> item.getSeverity() == ValidationSeverity.WARN);
    }

    /**
     * Prints the validation results to the console in a human-readable format.
     * <p>
     * The output format depends on whether any validation items were found:
     * <ul>
     *   <li>If no items exist, prints "No issues found."</li>
     *   <li>If items exist, prints a count summary followed by each issue prefixed with "*"</li>
     * </ul>
     * </p>
     * <p>
     * This method is useful for quick command-line feedback during validation runs.
     * </p>
     *
     * @see #writeResultsAsJson(File) for structured output to files
     */
    public void printResults() {
        if (validationItems.isEmpty()) {
            System.out.println(" No issues found.");
        } else {
            System.out.println(" Found " + validationItems.size() + " issue(s):");
            for (AbstractValidationItem item : validationItems) {
                System.out.println("* " + item);
            }
        }
    }

    /**
     * Writes the validation results to a JSON file in a human-readable format.
     * <p>
     * The generated JSON contains four main sections:
     * </p>
     * <ol>
     *   <li><b>{@code timestamp}</b>: The current time when the report is generated, formatted as {@code yyyy-MM-dd HH:mm:ss}</li>
     *   <li><b>{@code apiVersion}</b>: The current API version (v1, v2, or unknown) from {@link ApiVersionHolder}</li>
     *   <li><b>{@code summary}</b>: A map summarizing the total number of issues by severity level ({@code ERROR}, {@code WARN}, {@code INFO}, {@code SUCCESS})</li>
     *   <li><b>{@code validationItems}</b>: A sorted list of all validation issues found</li>
     * </ol>
     * <p>
     * <b>Sorting Algorithm:</b><br>
     * Validation items are sorted using a two-level approach:
     * <ol>
     *   <li>Primary: By severity level using {@link #SEVERITY_RANK} (ERROR → WARN → INFO → SUCCESS)</li>
     *   <li>Secondary: Alphabetically by their string representation for consistent ordering</li>
     * </ol>
     * </p>
     * <p>
     * <b>Error Handling:</b><br>
     * If an {@link IOException} occurs during file writing, an error message is printed
     * to {@code System.err} and the stack trace is displayed. The method does not throw
     * exceptions to avoid disrupting the validation workflow.
     * </p>
     *
     * @param outputFile the target file to which the JSON report will be written
     * @throws NullPointerException if outputFile is null
     *
     * @see #SEVERITY_RANK for the severity ordering used in sorting
     * @see ApiVersionHolder#getVersion() for API version determination
     */
    public void writeResultsAsJson(File outputFile) {
        // 0) Sort items - create a mutable copy first since record fields are immutable
        List<AbstractValidationItem> sortedItems = new ArrayList<>(validationItems);
        sortedItems.sort(
                Comparator.comparingInt((AbstractValidationItem i) ->
                                SEVERITY_RANK.getOrDefault(i.getSeverity(), Integer.MAX_VALUE))
                        .thenComparing(AbstractValidationItem::toString));

        /*  1) Build JSON root  */
        Map<String, Object> root = new LinkedHashMap<>();   // preserves insertion order

        // 1.1 Timestamp
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        root.put("timestamp", now);

        // 1.2 add API-Version using new enum
        ApiVersion apiVersion = ApiVersionHolder.getVersion();
        String versionStr = switch (apiVersion) {
            case V1 -> "v1";
            case V2 -> "v2";
            case UNKNOWN -> "unknown";
        };
        root.put("apiVersion", versionStr);

        // 1.3 Severity summary
        Map<ValidationSeverity, Long> bySeverity =
                validationItems.stream()
                        .collect(Collectors.groupingBy(AbstractValidationItem::getSeverity,
                                Collectors.counting()));

        Map<String, Long> summary = Map.of(
                "ERROR",   bySeverity.getOrDefault(ValidationSeverity.ERROR,   0L),
                "WARN",    bySeverity.getOrDefault(ValidationSeverity.WARN,    0L),
                "INFO",    bySeverity.getOrDefault(ValidationSeverity.INFO,    0L),
                "SUCCESS", bySeverity.getOrDefault(ValidationSeverity.SUCCESS, 0L)
        );
        root.put("summary", summary);

        /*  2) Validation items  */
        root.put("validationItems", sortedItems);

        /*  3) Write pretty-printed JSON  */
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            mapper.writeValue(outputFile, root);
        } catch (IOException e) {
            System.err.println("Failed to write JSON output to "
                    + outputFile.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the process ID associated with the first BPMN validation item found.
     * <p>
     * This method searches through the validation items to find the first instance of
     * {@link BpmnElementValidationItem} and extracts its process ID. The assumption is
     * that all BPMN validation items within a single validation run share the same
     * process ID, so using the first valid one is sufficient.
     * </p>
     * <p>
     * <b>Search Algorithm:</b>
     * <ol>
     *   <li>Iterate through all validation items</li>
     *   <li>Check if the item is an instance of {@code BpmnElementValidationItem}</li>
     *   <li>Extract the process ID and verify it's not null or empty</li>
     *   <li>Return the first valid process ID found</li>
     * </ol>
     * </p>
     * <p>
     * <b>Fallback Behavior:</b><br>
     * If no valid process ID can be determined (empty list, no BPMN items, or all have
     * null/empty process IDs), the method returns {@code "unknown_process"}.
     * </p>
     *
     * @return the process ID from the first BPMN validation item, or {@code "unknown_process"} if not available
     *
     * @see BpmnElementValidationItem#getProcessId()
     */
    public String getProcessId() {
        if (validationItems == null || validationItems.isEmpty())
            return "unknown_process";

        for (AbstractValidationItem item : validationItems) {
            if (item instanceof BpmnElementValidationItem bpmnItem) {
                String pid = bpmnItem.getProcessId();
                if (pid != null && !pid.isEmpty())
                    return pid;
            }
        }

        return "unknown_process";
    }

    /**
     * Creates an empty {@code ValidationOutput} instance with no validation items.
     * <p>
     * This factory method is useful for scenarios where validation completed successfully
     * without finding any issues, or as a default/placeholder instance. The returned
     * output will contain an empty list of validation items.
     * </p>
     * <p>
     * <b>Usage Examples:</b>
     * <ul>
     *   <li>Default return value when no validation issues are found</li>
     *   <li>Placeholder for initialization before actual validation runs</li>
     *   <li>Testing scenarios where empty results are needed</li>
     * </ul>
     * </p>
     *
     * @return a new {@code ValidationOutput} instance with an empty validation items list
     */
    public static ValidationOutput empty() {
        return new ValidationOutput(List.of());
    }

    /**
     * <h3>Severity Ranking for Validation Output Sorting</h3>
     *
     * <p>
     * Defines an explicit sort order for {@link ValidationSeverity} values used to prioritize
     * validation items in JSON reports. This ranking ensures that validation issues are sorted
     * in descending order of importance:
     * </p>
     *
     * <ol>
     *   <li>{@link ValidationSeverity#ERROR} → rank 0 (highest priority)</li>
     *   <li>{@link ValidationSeverity#WARN} → rank 1</li>
     *   <li>{@link ValidationSeverity#INFO} → rank 2</li>
     *   <li>{@link ValidationSeverity#SUCCESS} → rank 3 (lowest priority)</li>
     * </ol>
     *
     * <p>
     * This mapping is primarily used to sort the {@code validationItems} list before writing
     * it to {@code aggregated.json}, so that critical issues appear at the top of the report.
     * </p>
     *
     * <p>
     * Items with severities not explicitly listed in this map are assigned {@code Integer.MAX_VALUE}
     * and appear last in the output.
     * </p>
     */
    private static final Map<ValidationSeverity, Integer> SEVERITY_RANK = Map.of(
            ValidationSeverity.ERROR,   0,
            ValidationSeverity.WARN,    1,
            ValidationSeverity.INFO,    2,
            ValidationSeverity.SUCCESS, 3
    );
}