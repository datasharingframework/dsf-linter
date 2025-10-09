package dev.dsf.utils.validator.util.validation;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * <h2>DSF BPMN Validation Output</h2>
 *
 * <p>
 * The {@code ValidationOutput} class encapsulates the results of BPMN or FHIR validation runs.
 * It stores a list of {@link AbstractValidationItem} instances and provides methods to:
 * <ul>
 * <li>Print issues to the console</li>
 * <li>Export results as a pretty-printed JSON file with timestamp, API version, and summary</li>
 * <li>Retrieve associated process identifiers from BPMN validation items</li>
 * <li>Create empty validation outputs for cases with no issues</li>
 * </ul>
 * </p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 * <li><b>Immutable Design:</b> Implemented as a Java record for thread safety</li>
 * <li><b>Flexible Output:</b> Console printing and JSON file export</li>
 * <li><b>Smart Sorting:</b> Issues sorted by severity and description</li>
 * <li><b>API Versioning:</b> Includes API version information in JSON output</li>
 * <li><b>Summary Statistics:</b> Provides counts by validation severity</li>
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
 * // Create empty output for no issues
 * ValidationOutput empty = ValidationOutput.empty();
 * }</pre>
 *
 * <h3>JSON Output Format:</h3>
 * <pre>
 * {
 * "timestamp": "2025-04-11 16:58:23",
 * "apiVersion": "v2",
 * "summary": {
 * "ERROR": 2,
 * "WARN": 1,
 * "INFO": 0,
 * "SUCCESS": 5
 * },
 * "validationItems": [
 * { "severity": "ERROR", "message": "...", "location": "..." },
 * { "severity": "WARN", "message": "...", "location": "..." }
 * ]
 * }
 * </pre>
 * <p>
 * The JSON output includes:
 * <ul>
 * <li><b>timestamp:</b> Report generation time in {@code yyyy-MM-dd HH:mm:ss} format</li>
 * <li><b>apiVersion:</b> Current API version (v1, v2, or unknown)</li>
 * <li><b>summary:</b> Count of issues by severity level</li>
 * <li><b>validationItems:</b> Sorted list of validation issues</li>
 * </ul>
 * </p>
 *
 * @param validationItems the list of validation items found during validation
 *
 * @see AbstractValidationItem
 * @see BpmnElementValidationItem
 * @see ValidationSeverity
 */
public record ValidationOutput(List<AbstractValidationItem> validationItems)
{
    /**
     * A map that defines the sort order for validation severities.
     */
    public static final Map<ValidationSeverity, Integer> SEVERITY_RANK = Map.of(
            ValidationSeverity.ERROR,   0,
            ValidationSeverity.WARN,    1,
            ValidationSeverity.INFO,    2,
            ValidationSeverity.SUCCESS, 3
    );

    /**
     * Constructs a {@code ValidationOutput} and immediately sorts the validation items.
     * <p>
     * The items are sorted first by severity (ERROR, WARN, INFO, SUCCESS) and then
     * alphabetically by their string representation to ensure a consistent order.
     * </p>
     *
     * @param validationItems the list of validation items to be stored and sorted.
     */
    public ValidationOutput(List<AbstractValidationItem> validationItems)
    {
        this.validationItems = new ArrayList<>(validationItems); // Create a mutable copy
        this.validationItems.sort(
                Comparator.comparingInt((AbstractValidationItem i) ->
                                SEVERITY_RANK.getOrDefault(i.getSeverity(), Integer.MAX_VALUE))
                        .thenComparing(AbstractValidationItem::toString)
        );
    }

    /**
     * Returns the sorted list of validation items.
     *
     * @return a sorted, unmodifiable view of the validation items list.
     */
    @Override
    public List<AbstractValidationItem> validationItems()
    {
        return Collections.unmodifiableList(validationItems);
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
     * Prints the validation results to the console.
     */
    public void printResults(Logger logger)
    {
        if (validationItems.isEmpty())
        {
            System.out.println(" No issues found.");
        }
        else
        {
            System.out.println(" Found " + validationItems.size() + " issue(s):");
            for (AbstractValidationItem item : validationItems)
            {
                System.out.println("* " + item);
            }
        }
    }


    /**
     * Retrieves the process ID from the first BPMN validation item.
     *
     * @return the process ID or "unknown_process".
     */
    public String getProcessId()
    {
        if (validationItems == null || validationItems.isEmpty())
            return "unknown_process";

        for (AbstractValidationItem item : validationItems)
        {
            if (item instanceof BpmnElementValidationItem bpmnItem)
            {
                String pid = bpmnItem.getProcessId();
                if (pid != null && !pid.isEmpty())
                    return pid;
            }
        }

        return "unknown_process";
    }

    /**
     * Creates an empty ValidationOutput instance.
     *
     * @return an empty ValidationOutput.
     */
    public static ValidationOutput empty()
    {
        return new ValidationOutput(List.of());
    }
}