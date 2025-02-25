package dev.dsf.utils.validator;

import dev.dsf.utils.validator.bpmn.BpmnModelValidator;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;
import dev.dsf.utils.validator.item.UnparsableBpmnFileValidationItem;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implementation of the DSF Validator interface, responsible for validating BPMN models.
 *
 * <p>This class performs the following steps:
 * <ol>
 *   <li>Checks if the BPMN file exists and is readable.</li>
 *   <li>Parses the BPMN file into a {@link BpmnModelInstance}.</li>
 *   <li>Extracts the first {@link Process} ID found within the model.</li>
 *   <li>Locates the project root directory by searching upward for a {@code pom.xml} file.</li>
 *   <li>Instantiates and uses a {@link BpmnModelValidator} to validate the BPMN model,
 *       passing along the project root for additional checks (e.g., class existence).</li>
 *   <li>Compiles all validation issues into a {@link ValidationOutput} object.</li>
 * </ol>
 * </p>
 *
 * <p>This implementation also adds {@link UnparsableBpmnFileValidationItem} issues when
 * the file is either missing, unreadable, or cannot be parsed as BPMN.</p>
 *
 * @see BpmnModelValidator
 * @see ValidationOutput
 */
public class DsfValidatorImpl implements DsfValidator {

    /**
     * Validates the provided BPMN file and returns a {@link ValidationOutput} containing any validation issues.
     *
     * <p>This method does the following:
     * <ul>
     *   <li>Verifies that the file at {@code path} exists and is readable. If not, an error is recorded.</li>
     *   <li>Attempts to parse the file as a BPMN model. If parsing fails, an error is recorded.</li>
     *   <li>Extracts the first encountered BPMN {@link Process} ID from the model.</li>
     *   <li>Determines the project root directory by searching parent directories for a {@code pom.xml} file.</li>
     *   <li>Uses a {@link BpmnModelValidator} to validate the model, passing the project root for class resolution.</li>
     *   <li>Returns a {@link ValidationOutput} containing all collected validation issues (both parsing and BPMN-level).</li>
     * </ul>
     * </p>
     *
     * @param path the path to the BPMN file to validate (must exist and be readable)
     * @return a {@link ValidationOutput} containing all validation issues encountered
     */
    @Override
    public ValidationOutput validate(Path path) {
        List<AbstractValidationItem> allIssues = new ArrayList<>();

        // Check if the file exists
        if (!Files.exists(path)) {
            System.err.println("❌ Error: The file does not exist: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // Check if the file is readable
        if (!Files.isReadable(path)) {
            System.err.println("❌ Error: The file is not readable: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // Attempt to parse the BPMN file
        BpmnModelInstance model;
        try {
            model = Bpmn.readModelFromFile(path.toFile());
        } catch (Exception e) {
            System.err.println("❌ Error reading BPMN file: " + e.getMessage());
            e.printStackTrace();
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // Extract the process id from the BPMN model.
        String processId = extractProcessId(model);

        // Determine the project root by searching upward for a pom.xml file.
        File projectRoot = getProjectRoot(path);

        // Perform BPMN validation using BpmnModelValidator.
        BpmnModelValidator validator = new BpmnModelValidator();
        validator.setProjectRoot(projectRoot);

        // Validate the model using the extracted process id.
        List<BpmnElementValidationItem> bpmnIssues = validator.validateModel(model, path.toFile(), processId);
        allIssues.addAll(bpmnIssues);

        return buildOutput(allIssues);
    }

    /**
     * Constructs a {@link ValidationOutput} from a list of validation items.
     *
     * <p>The returned {@link ValidationOutput} encapsulates the final state of the validation,
     * including any errors, warnings, or informational messages gathered throughout the process.
     * </p>
     *
     * @param items a list of validation items that represent specific validation findings
     * @return a {@link ValidationOutput} containing the provided items
     */
    private ValidationOutput buildOutput(List<AbstractValidationItem> items) {
        return new ValidationOutput(items);
    }

    /**
     * Determines the project root by walking up the directory tree from the BPMN file path
     * and searching for a file named {@code pom.xml}.
     *
     * <p>If a {@code pom.xml} file is found, the directory containing it is assumed to be
     * the Maven project root. If no {@code pom.xml} is found, this method falls back
     * to the parent directory of the BPMN file.
     * </p>
     *
     * @param bpmnFilePath the path to the BPMN file
     * @return the project root directory as a {@link File}, never {@code null}
     */
    private File getProjectRoot(Path bpmnFilePath) {
        Path current = bpmnFilePath.getParent();
        while (current != null) {
            File pom = new File(current.toFile(), "pom.xml");
            if (pom.exists()) {
                return current.toFile();
            }
            current = current.getParent();
        }
        // Fallback to the BPMN file's parent directory if no pom.xml was located.
        return bpmnFilePath.getParent().toFile();
    }

    /**
     * Extracts the {@code id} of the first {@link Process} element found in the BPMN model.
     *
     * <p>If the model contains no {@code Process} elements, or if the {@code id} is empty,
     * this method returns an empty string instead.</p>
     *
     * @param model the parsed {@link BpmnModelInstance} of the BPMN file
     * @return the extracted process id, or an empty string if none is found
     */
    private String extractProcessId(BpmnModelInstance model) {
        Collection<Process> processes = model.getModelElementsByType(Process.class);
        if (!processes.isEmpty()) {
            Process process = processes.iterator().next();
            if (!isEmpty(process.getId())) {
                return process.getId();
            }
        }
        return "";
    }

    /**
     * Determines if the given string is null or consists only of whitespace characters.
     *
     * @param value the string to check
     * @return {@code true} if {@code value} is {@code null} or empty after trimming; {@code false} otherwise
     */
    private boolean isEmpty(String value) {
        return (value == null || value.trim().isEmpty());
    }
}
