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
 * Implementation of the DSF Validator interface.
 * This class is responsible for validating BPMN models.
 * It parses the BPMN file, extracts the process ID from the BPMN model,
 * determines the project root by searching upward for a pom.xml file, and then
 * performs BPMN validation using a {@link BpmnModelValidator}.
 */
public class DsfValidatorImpl implements DsfValidator {

    /**
     * Validates the provided BPMN file.
     *
     * <p>
     * This method checks that the file exists and is readable, attempts to parse the BPMN file,
     * determines the project root (by searching upward for a pom.xml file), extracts the process ID
     * from the BPMN model, and then performs BPMN validation using a {@link BpmnModelValidator}.
     * </p>
     *
     * @param path the path to the BPMN file.
     * @return a {@link ValidationOutput} containing all validation issues.
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
        //System.out.println("DEBUG: Extracted processId: " + processId);

        // Determine the project root by searching upward for a pom.xml file.
        File projectRoot = getProjectRoot(path);
        //System.out.println("DEBUG: Project root determined as: " + projectRoot.getAbsolutePath());

        // Perform BPMN validation using BpmnModelValidator.
        BpmnModelValidator validator = new BpmnModelValidator();
        // Pass the project root so that the validator can locate compiled classes.
        validator.setProjectRoot(projectRoot);

        // Validate the model using the extracted process id.
        List<BpmnElementValidationItem> bpmnIssues = validator.validateModel(model, path.toFile(), processId);
        allIssues.addAll(bpmnIssues);

        return buildOutput(allIssues);
    }

    /**
     * Constructs a {@link ValidationOutput} from a list of validation items.
     *
     * @param items the list of validation items.
     * @return a {@link ValidationOutput} containing the provided items.
     */
    private ValidationOutput buildOutput(List<AbstractValidationItem> items) {
        return new ValidationOutput(items);
    }

    /**
     * Determines the project root by walking up the directory tree from the BPMN file.
     * This method searches for a "pom.xml" file, assuming that the project root is the directory
     * that contains the pom.xml. If no pom.xml is found, it falls back to the BPMN file's parent.
     *
     * @param bpmnFilePath the path to the BPMN file.
     * @return the project root as a File.
     */
    private File getProjectRoot(Path bpmnFilePath) {
        //System.out.println("DEBUG: Starting project root determination from: " + bpmnFilePath.toAbsolutePath());
        Path current = bpmnFilePath.getParent();
        while (current != null) {
            File pom = new File(current.toFile(), "pom.xml");
            //System.out.println("DEBUG: Checking for pom.xml in: " + current.toAbsolutePath());
            if (pom.exists()) {
                //System.out.println("DEBUG: Found pom.xml at: " + current.toAbsolutePath());
                return current.toFile();
            }
            current = current.getParent();
        }
        //System.out.println("DEBUG: pom.xml not found; using BPMN file's parent as fallback.");
        return bpmnFilePath.getParent().toFile();
    }

    /**
     * Extracts the process id from the BPMN model.
     * It retrieves the first process element from the model and returns its id.
     *
     * @param model the BPMN model instance.
     * @return the process id, or an empty string if not found.
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
     * Checks if the given string is null or empty.
     *
     * @param value the string to check.
     * @return true if empty, false otherwise.
     */
    private boolean isEmpty(String value) {
        return (value == null || value.trim().isEmpty());
    }
}
