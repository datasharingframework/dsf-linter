package dev.dsf.utils.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
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
 *   <li>Export results as a pretty-printed JSON file</li>
 *   <li>Retrieve associated process identifiers</li>
 * </ul>
 * </p>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * List<AbstractValidationItem> items = ...;
 * ValidationOutput output = new ValidationOutput(items);
 * output.printResults();
 * output.writeResultsAsJson(new File("issues.json"));
 * }</pre>
 *
 * <h3>JSON Output Format:</h3>
 * <pre>
 * {
 *   "timestamp": "2025-04-11 16:58:23",
 *   "validationItems": [ ... ]
 * }
 * </pre>
 * <p>The output includes a timestamp in {@code yyyy-MM-dd HH:mm:ss} format and a list of sorted items.</p>
 *
 * <h3>References:</h3>
 * <ul>
 *   <li><a href="https://github.com/FasterXML/jackson-databind">Jackson Databind</a></li>
 *   <li><a href="https://picocli.info/">picocli</a></li>
 *   <li><a href="https://maven.apache.org/">Apache Maven</a></li>
 * </ul>
 */
public record ValidationOutput(List<AbstractValidationItem> validationItems)
{
    /**
     * Constructs a {@code ValidationOutput} with the given list of validation items.
     *
     * @param validationItems the items to be stored
     */
    public ValidationOutput
    {
    }

    /**
     * Returns the validation items stored in this output.
     *
     * @return a list of validation items
     */
    @Override
    public List<AbstractValidationItem> validationItems()
    {
        return validationItems;
    }

    /**
     * Prints the validation results to the console.
     * If no items exist, prints "No issues found."
     * Otherwise, prints each issue on its own line.
     */
    public void printResults()
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
     * Writes the validation results to a JSON file in a human-readable format.
     * <p>
     * The generated JSON contains three main sections:
     * </p>
     * <ol>
     *   <li><b>{@code timestamp}</b>: The current time when the report is generated, formatted as {@code yyyy-MM-dd HH:mm:ss}.</li>
     *   <li><b>{@code summary}</b>: A map summarizing the total number of issues by severity level ({@code ERROR}, {@code WARN}, {@code INFO}, {@code SUCCESS}).</li>
     *   <li><b>{@code validationItems}</b>: A sorted list of all validation issues found.</li>
     * </ol>
     * <p>
     * Validation items are first sorted by severity (as defined by the {@link #SEVERITY_RANK}) and then alphabetically
     * by their string representation. The output is written as a pretty-printed JSON file.
     * </p>
     *
     * @param outputFile the target file to which the report will be written
     */
    public void writeResultsAsJson(File outputFile)
    {
        // 0) Sort items (existing logic, kept)
        validationItems.sort(
                Comparator.comparingInt((AbstractValidationItem i) ->
                                SEVERITY_RANK.getOrDefault(i.getSeverity(), Integer.MAX_VALUE))
                        .thenComparing(AbstractValidationItem::toString));

        /*  1) Build JSON root  */
        Map<String, Object> root = new LinkedHashMap<>();   // preserves insertion order

        // 1.1 Timestamp
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        root.put("timestamp", now);

        // 1.2 add API-Version
        root.put("apiVersion", ApiVersionHolder.getVersion());

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
        root.put("validationItems", validationItems);

        /*  3) Write pretty-printed JSON  */
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try
        {
            mapper.writeValue(outputFile, root);
        }
        catch (IOException e)
        {
            System.err.println("Failed to write JSON output to "
                    + outputFile.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * <p>
     * Returns the process ID associated with the BPMN file that was validated.
     * This method attempts to invoke a {@code getProcessId()} method on the first
     * BPMN validation item. It assumes that all validation items in this output
     * share the same process ID.
     * </p>
     *
     * <p>
     * If no process ID can be determined, it returns "unknown_process".
     * </p>
     *
     * @return the process ID, or "unknown_process" if not available
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
    public static ValidationOutput empty()
    {
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
