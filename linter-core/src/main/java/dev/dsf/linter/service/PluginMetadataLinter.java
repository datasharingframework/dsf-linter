package dev.dsf.linter.service;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.output.item.PluginLintItem;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for linting high-level plugin metadata.
 * This checks the plugin's definition rather than its resource files.
 */
public final class PluginMetadataLinter {

    private PluginMetadataLinter() {
    }

    /**
     * lints plugin metadata, such as the presence of declared resources.
     * This is called by the PluginLintingOrchestrator as an additional check.
     *
     * @param plugin The plugin adapter to lint.
     * @param projectPath The path to the project for context in lint items.
     * @return A list of lint items related to metadata.
     */
    public static List<AbstractLintItem> lintPluginMetadata(
            PluginAdapter plugin, Path projectPath) {
        List<AbstractLintItem> items = new ArrayList<>();

        // Check for process models
        if (plugin.getProcessModels().isEmpty()) {
            items.add(new PluginLintItem(
                    LinterSeverity.WARN, LintingType.PLUGIN_DEFINITION_NO_PROCESS_MODEL_DEFINED,
                    projectPath.toFile(),
                    plugin.sourceClass().getName(),
                    "Warning: No BPMN process models are defined in this plugin."
            ));
        } else {
            // Success case: Process models are defined
            items.add(PluginLintItem.success(
                    projectPath.toFile(),
                    plugin.sourceClass().getName(),
                    "Process models are defined in this plugin."
            ));
        }

        // Check for FHIR resources
        if (plugin.getFhirResourcesByProcessId().isEmpty()) {
            items.add(new PluginLintItem(
                    LinterSeverity.WARN, LintingType.PLUGIN_DEFINITION_NO_FHIR_RESOURCES_DEFINED,
                    projectPath.toFile(),
                    plugin.sourceClass().getName(),
                    "Warning: No FHIR resources are defined in this plugin."
            ));
        } else {
            // Success case: FHIR resources are defined
            items.add(PluginLintItem.success(
                    projectPath.toFile(),
                    plugin.sourceClass().getName(),
                    "FHIR resources are defined in this plugin."
            ));
        }

        return items;
    }
}
