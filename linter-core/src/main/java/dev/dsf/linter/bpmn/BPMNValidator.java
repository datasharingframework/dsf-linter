package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.item.BpmnElementValidationItem;
import dev.dsf.linter.output.item.PluginDefinitionUnparsableBpmnResourceValidationItem;
import dev.dsf.linter.util.validation.ValidationOutput;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.dsf.linter.util.validation.ValidationUtils.getFile;

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
     * If parsing fails, it returns a ValidationOutput with a PluginDefinitionUnparsableBpmnFileValidationItem
     * instead of throwing an exception, allowing the validation process to continue with other plugins.
     *
     * @param bpmnFilePath the path to the BPMN file.
     * @return a ValidationOutput containing all validation issues.
     */
    public ValidationOutput validateBpmnFile(Path bpmnFilePath) {
        try {
            BpmnModelInstance model = Bpmn.readModelFromFile(bpmnFilePath.toFile());

            File bpmnFile = bpmnFilePath.toFile();
            File projectRoot = getProjectRoot(bpmnFilePath);

            // Delegate content validation to the model validator
            BpmnModelValidator modelValidator = new BpmnModelValidator(projectRoot);
            List<BpmnElementValidationItem> items = modelValidator.validateModel(model, bpmnFile);

            return new ValidationOutput(new ArrayList<>(items));
        } catch (Exception e) {
            String pluginName = getProjectRoot(bpmnFilePath).getName();
            PluginDefinitionUnparsableBpmnResourceValidationItem errorItem = getPluginDefinitionUnparsableBpmnResourceValidationItem(bpmnFilePath, pluginName);

            return new ValidationOutput(Collections.singletonList(errorItem));
        }
    }

    private static PluginDefinitionUnparsableBpmnResourceValidationItem getPluginDefinitionUnparsableBpmnResourceValidationItem(Path bpmnFilePath, String pluginName) {
        String fileName = bpmnFilePath.getFileName().toString();
        String errorMessage = String.format(
                "Validation for plugin \"%s\" may has some false items because the file \"%s\" is unparsable.",
                pluginName,
                fileName
        );

        return new PluginDefinitionUnparsableBpmnResourceValidationItem(
                bpmnFilePath.toFile(),
                pluginName,
                errorMessage
        );
    }

    /**
     * Attempts to find the project's root directory by traversing up from the given path
     * and looking for a "pom.xml" file.
     *
     * @param filePath The path to start searching from.
     * @return The project root directory, or the parent of the file as a fallback.
     */
    private File getProjectRoot(Path filePath) {
        return getFile(filePath);
    }
}