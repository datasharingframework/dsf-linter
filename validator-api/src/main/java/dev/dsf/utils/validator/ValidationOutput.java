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

/**
 * <p>
 * The {@code ValidationOutput} class encapsulates a list of validation items (issues)
 * found during BPMN validation processes. It provides methods for both console-based
 * and JSON-based output.
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>
 *   ValidationOutput output = new ValidationOutput(items);
 *   output.printResults();
 *   output.writeResultsAsJson(new File("issues.json"));
 * </pre>
 * </p>
 *
 * <p>
 * When writing to JSON, the following structure is produced:
 * </p>
 * <pre>
 * {
 *   "timestamp": "2025-04-11 16:58:23",
 *   "validationItems": [ ... ]
 * }
 * </pre>
 * <p>
 * The timestamp is always included as the first field, using a {@code yyyy-MM-dd HH:mm:ss} format.
 * The list of validation items is sorted (by {@link AbstractValidationItem#toString()})
 * for consistency.
 * </p>
 *
 * <p><strong>References:</strong></p>
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
     * <p>
     * Writes the validation items to a JSON file, always including a timestamp
     * as the first field in the output. The JSON file is pretty-printed for readability.
     * </p>
     *
     * <p>
     * The internal list of items is sorted by their string representation before being written.
     * </p>
     *
     * @param outputFile the target JSON file
     */
    public void writeResultsAsJson(File outputFile)
    {
        // Sort items by their string value (you can change this comparator if needed)
        validationItems.sort(Comparator.comparing(AbstractValidationItem::toString));

        // Build a root structure that includes the timestamp and the list of items
        Map<String, Object> root = new LinkedHashMap<>();

        // 1) Insert a timestamp in "yyyy-MM-dd HH:mm:ss" format
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        root.put("timestamp", now);

        // 2) Add the sorted validation items
        root.put("validationItems", validationItems);

        // 3) Write to the outputFile with Jackson (pretty-printed)
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try
        {
            mapper.writeValue(outputFile, root);
        }
        catch (IOException e)
        {
            System.err.println("Failed to write JSON output to " + outputFile.getAbsolutePath() + ": " + e.getMessage());
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
}
