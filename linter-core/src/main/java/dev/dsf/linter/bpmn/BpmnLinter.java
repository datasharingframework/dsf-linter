package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.item.BpmnElementLintItem;
import dev.dsf.linter.output.item.PluginDefinitionUnparsableBpmnResourceLintItem;
import dev.dsf.linter.util.linting.LintingOutput;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.dsf.linter.util.linting.LintingUtils.getFile;

/**
 * BpmnLinter is the primary entry point for linting a single BPMN file.
 * It handles the initial parsing of the file and then delegates the content
 * linting to the BpmnModelLinter.
 * <p>
 * This class is designed to be a self-contained utility.
 */
public class BpmnLinter {

    /**
     * lints the BPMN file located at the given path.
     * If parsing fails, it returns a LintOutput with a PluginDefinitionUnparsableBpmnFileLintItem
     * instead of throwing an exception, allowing the linting process to continue with other plugins.
     *
     * @param bpmnFilePath the path to the BPMN file.
     * @return a LintingOutput containing all lint issues.
     */
    public LintingOutput lintBpmnFile(Path bpmnFilePath) {
        try {
            BpmnModelInstance model = Bpmn.readModelFromFile(bpmnFilePath.toFile());

            File bpmnFile = bpmnFilePath.toFile();
            File projectRoot = getProjectRoot(bpmnFilePath);

            // Delegate content linting to the model linter
            BpmnModelLinter modelLinter = new BpmnModelLinter(projectRoot);
            List<BpmnElementLintItem> items = modelLinter.lintModel(model, bpmnFile);

            return new LintingOutput(new ArrayList<>(items));
        } catch (Exception e) {
            String pluginName = getProjectRoot(bpmnFilePath).getName();
            PluginDefinitionUnparsableBpmnResourceLintItem errorItem = getPluginDefinitionUnparsableBpmnResourceLintItem(bpmnFilePath, pluginName);

            return new LintingOutput(Collections.singletonList(errorItem));
        }
    }

    private static PluginDefinitionUnparsableBpmnResourceLintItem getPluginDefinitionUnparsableBpmnResourceLintItem(Path bpmnFilePath, String pluginName) {
        String fileName = bpmnFilePath.getFileName().toString();
        String errorMessage = String.format(
                "linting for plugin \"%s\" may has some false items because the file \"%s\" is unparsable.",
                pluginName,
                fileName
        );

        return new PluginDefinitionUnparsableBpmnResourceLintItem(
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