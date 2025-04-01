package dev.dsf.utils.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * The {@code ValidationOutput} class encapsulates a list of validation items
 * (issues) found during BPMN or FHIR validation processes. It provides methods
 * for both console-based and JSON-based output.
 *
 * <p>
 * Usage example:
 * <pre>
 *   ValidationOutput output = new ValidationOutput(items);
 *   output.printResults();
 *   output.writeResultsAsJson(new File("issues.json"));
 * </pre>
 * </p>
 *
 * <p>References:</p>
 * <ul>
 *   <li><a href="https://github.com/FasterXML/jackson-databind">Jackson Databind</a></li>
 *   <li><a href="https://picocli.info/">picocli</a></li>
 *   <li><a href="https://maven.apache.org/">Apache Maven</a></li>
 * </ul>
 */
public record ValidationOutput(List<AbstractValidationItem> validationItems) {
    /**
     * Constructs a {@code ValidationOutput} with the given list of validation items.
     *
     * @param validationItems the items to be stored
     */
    public ValidationOutput {
    }

    /**
     * Returns the validation items stored in this output.
     *
     * @return a list of validation items
     */
    @Override
    public List<AbstractValidationItem> validationItems() {
        return validationItems;
    }

    /**
     * Prints the validation results to the console.
     * If no items exist, prints "No issues found."
     * Otherwise, prints each issue on its own line.
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
     * Writes the validation items to a JSON file, sorted by their string representation.
     * The JSON file will be pretty-printed for human readability.
     *
     * @param outputFile the target JSON file
     */
    public void writeResultsAsJson(File outputFile) {
        // Sort items by their string value (can be changed to any other comparator)
        validationItems.sort(Comparator.comparing(AbstractValidationItem::toString));

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            mapper.writeValue(outputFile, validationItems);
        } catch (IOException e) {
            System.err.println("Failed to write JSON output: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Returns the process ID associated with the BPMN file that was validated.
     * This method attempts to invoke a {@code getProcessId()} method on the first validation item.
     * It assumes that all validation items in this output share the same process ID.
     * If no process ID can be determined, it returns "unknown_process".
     *
     * @return the process ID, or "unknown_process" if not available
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


}
