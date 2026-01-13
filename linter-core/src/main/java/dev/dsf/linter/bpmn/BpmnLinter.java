package dev.dsf.linter.bpmn;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.BpmnElementLintItem;
import dev.dsf.linter.output.item.PluginLintItem;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.util.linting.LintingUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BpmnLinter is the primary entry point for linting a single BPMN file.
 */
public class BpmnLinter {

    public LintingOutput lintBpmnFile(Path bpmnFilePath) {
        try {
            BpmnModelInstance model = Bpmn.readModelFromFile(bpmnFilePath.toFile());

            File bpmnFile = bpmnFilePath.toFile();
            File projectRoot = getProjectRoot(bpmnFilePath);

            BpmnModelLinter modelLinter = new BpmnModelLinter(projectRoot);
            List<BpmnElementLintItem> items = modelLinter.lintModel(model, bpmnFile);

            return new LintingOutput(new ArrayList<>(items));
        } catch (Exception e) {
            String pluginName = getProjectRoot(bpmnFilePath).getName();
            PluginLintItem errorItem = createUnparsableBpmnResourceItem(bpmnFilePath, pluginName);

            return new LintingOutput(Collections.singletonList(errorItem));
        }
    }

    private static PluginLintItem createUnparsableBpmnResourceItem(Path bpmnFilePath, String pluginName) {
        String fileName = bpmnFilePath.getFileName().toString();
        String errorMessage = String.format(
                "linting for plugin \"%s\" may has some false items because the file \"%s\" is unparsable.",
                pluginName,
                fileName
        );

        return new PluginLintItem(
                LinterSeverity.ERROR,
                LintingType.PLUGIN_DEFINITION_UNPARSABLE_BPMN_RESOURCE,
                bpmnFilePath.toFile(),
                pluginName,
                errorMessage
        );
    }

    private File getProjectRoot(Path filePath) {
        return LintingUtils.getProjectRoot(filePath);
    }
}
