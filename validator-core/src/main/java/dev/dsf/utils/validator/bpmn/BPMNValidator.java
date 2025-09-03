package dev.dsf.utils.validator.bpmn;

import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.item.BpmnElementValidationItem;
import dev.dsf.utils.validator.util.validation.ValidationOutput;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * BPMNValidator is the primary entry point for validating a single BPMN file.
 * It handles the initial parsing of the file and then delegates the content
 * validation to the BpmnModelValidator.
 * <p>
 * This class is designed to be a self-contained utility.
 */
public class BPMNValidator {

    /**
     * Validates the BPMN file located at the given path.
     * This method first parses the file. If parsing fails due to syntax errors,
     * it throws a ResourceValidationException to halt the entire validation process.
     *
     * @param bpmnFilePath the path to the BPMN file.
     * @return a ValidationOutput containing all validation issues if parsing is successful.
     * @throws ResourceValidationException if the BPMN file cannot be parsed.
     */
    public ValidationOutput validateBpmnFile(Path bpmnFilePath) throws ResourceValidationException {
        BpmnModelInstance model;
        try {
            model = Bpmn.readModelFromFile(bpmnFilePath.toFile());
        } catch (Exception e) {
            // Requirement 7: Abort on syntax error by throwing the fatal exception.
            throw new ResourceValidationException("Failed to parse BPMN model", bpmnFilePath, e);
        }

        File bpmnFile = bpmnFilePath.toFile();
        File projectRoot = getProjectRoot(bpmnFilePath);

        // Delegate content validation to the model validator
        BpmnModelValidator modelValidator = new BpmnModelValidator(projectRoot);
        List<BpmnElementValidationItem> items = modelValidator.validateModel(model, bpmnFile);

        // Extract processId from the validation items for the final output object
        String processId = items.stream()
                .map(BpmnElementValidationItem::getProcessId)
                .findFirst()
                .orElse("");

        return new ValidationOutput(new ArrayList<>(items));
    }

    /**
     * Attempts to find the project's root directory by traversing up from the given path
     * and looking for a "pom.xml" file.
     *
     * @param filePath The path to start searching from.
     * @return The project root directory, or the parent of the file as a fallback.
     */
    private File getProjectRoot(Path filePath) {
        Path current = filePath.getParent();
        while (current != null) {
            if (new File(current.toFile(), "pom.xml").exists()) {
                return current.toFile();
            }
            current = current.getParent();
        }
        // Fallback to the file's direct parent if no pom.xml is found
        return filePath.getParent() != null ? filePath.getParent().toFile() : new File(".");
    }
}