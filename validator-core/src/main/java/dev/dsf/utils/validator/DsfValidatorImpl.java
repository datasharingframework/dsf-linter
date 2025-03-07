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
 *   <li>Checks if the BPMN file exists.</li>
 *   <li>Checks if the BPMN file is readable (via {@link #isFileReadable(Path)}).</li>
 *   <li>Parses the BPMN file into a {@link BpmnModelInstance}.</li>
 *   <li>Extracts the first {@link Process} ID found within the model.</li>
 *   <li>Locates the project root directory by searching upward for a {@code pom.xml} file.</li>
 *   <li>Instantiates and uses a {@link BpmnModelValidator} to validate the BPMN model,
 *       passing along the project root for additional checks (e.g., class existence).</li>
 *   <li>Compiles all validation issues into a {@link ValidationOutput} object.</li>
 * </ol>
 */
public class DsfValidatorImpl implements DsfValidator {

    /**
     * Validates the provided BPMN file and returns a {@link ValidationOutput} containing any validation issues.
     *
     * <p>This method does the following:
     * <ul>
     *   <li>Verifies that the file at {@code path} exists.</li>
     *   <li>Checks readability via {@link #isFileReadable(Path)}, which can be mocked in tests.</li>
     *   <li>Attempts to parse the file as a BPMN model. If parsing fails, an error is recorded.</li>
     *   <li>Extracts the first encountered BPMN {@link Process} ID from the model.</li>
     *   <li>Determines the project root directory by searching parent directories for a {@code pom.xml} file.</li>
     *   <li>Uses a {@link BpmnModelValidator} to validate the model.</li>
     *   <li>Returns a {@link ValidationOutput} containing all collected validation issues.</li>
     * </ul>
     *
     * @param path the path to the BPMN file to validate (must exist and be readable)
     * @return a {@link ValidationOutput} containing all validation issues encountered
     */
    @Override
    public ValidationOutput validate(Path path) {
        List<AbstractValidationItem> allIssues = new ArrayList<>();

        // 1) Check if the file exists
        if (!Files.exists(path)) {
            System.err.println("Error: The file does not exist: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // 2) Check if the file is readable (via isFileReadable) - can be mocked in tests
        if (!isFileReadable(path)) {
            System.err.println("Error: The file is not readable: " + path);
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // 3) Attempt to parse the BPMN file
        BpmnModelInstance model;
        try {
            model = Bpmn.readModelFromFile(path.toFile());
        } catch (Exception e) {
            System.err.println("Error reading BPMN file: " + e.getMessage());
            e.printStackTrace();
            allIssues.add(new UnparsableBpmnFileValidationItem(ValidationSeverity.ERROR));
            return buildOutput(allIssues);
        }

        // 4) Extract the process id from the BPMN model
        String processId = extractProcessId(model);

        // 5) Determine the project root by searching upward for a pom.xml file
        File projectRoot = getProjectRoot(path);

        // 6) Perform BPMN validation using BpmnModelValidator
        BpmnModelValidator validator = new BpmnModelValidator();
        validator.setProjectRoot(projectRoot);

        // Validate the model using the extracted process id.
        List<BpmnElementValidationItem> bpmnIssues = validator.validateModel(model, path.toFile(), processId);
        allIssues.addAll(bpmnIssues);

        return buildOutput(allIssues);
    }

    /**
     * Checks the readability of the file, using {@link Files#isReadable(Path)} by default.
     * <p>
     * This method can be overridden or mocked in tests to simulate an unreadable file
     * without actually changing OS file permissions.
     *
     * @param path the BPMN file path
     * @return true if the file is readable, false otherwise
     */
    protected boolean isFileReadable(Path path) {
        return Files.isReadable(path);
    }

    /**
     * Constructs a {@link ValidationOutput} from a list of validation items.
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
        // Fallback to the BPMN file's parent directory if no pom.xml was located
        assert bpmnFilePath.getParent() != null;
        return bpmnFilePath.getParent().toFile();
    }

    /**
     * Extracts the {@code id} of the first {@link Process} element found in the BPMN model.
     *
     * @param model the parsed {@link BpmnModelInstance} of the BPMN file
     * @return the extracted process id, or an empty string if none is found
     */
    private String extractProcessId(BpmnModelInstance model) {
        Collection<Process> processes = model.getModelElementsByType(Process.class);
        if (!processes.isEmpty()) {
            Process process = processes.iterator().next();
            if (process.getId() != null && !process.getId().trim().isEmpty()) {
                return process.getId();
            }
        }
        return "";
    }
}
