// validator-core/src/main/java/dev/dsf/utils/validator/service/PluginMetadataValidator.java

package dev.dsf.linter.service;

import dev.dsf.linter.item.AbstractValidationItem;
import dev.dsf.linter.item.PluginDefinitionNoFhirResourcesDefinedValidationItem;
import dev.dsf.linter.item.PluginDefinitionNoProcessModelDefinedValidationItem;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for validating high-level plugin metadata.
 * This checks the plugin's definition rather than its resource files.
 */
public final class PluginMetadataValidator {

    private PluginMetadataValidator() {

    }

    /**
     * Validates plugin metadata, such as the presence of declared resources.
     * This is called by the ValidationOrchestrator as an additional check.
     *
     * @param plugin The plugin adapter to validate.
     * @param projectPath The path to the project for context in validation items.
     * @return A list of validation items related to metadata.
     */
    public static List<AbstractValidationItem> validatePluginMetadata(
            PluginAdapter plugin, Path projectPath) {

        List<AbstractValidationItem> items = new ArrayList<>();

        // Check for empty process models
        if (plugin.getProcessModels().isEmpty()) {
            items.add(new PluginDefinitionNoProcessModelDefinedValidationItem(
                    projectPath.toFile(),
                    plugin.sourceClass().getName(),
                    "Warning: No BPMN process models are defined in this plugin."
            ));
        }

        // Check for empty FHIR resources
        if (plugin.getFhirResourcesByProcessId().isEmpty()) {
            items.add(new PluginDefinitionNoFhirResourcesDefinedValidationItem(
                    projectPath.toFile(),
                    plugin.sourceClass().getName(),
                    "Warning: No FHIR resources are defined in this plugin."
            ));
        }

        return items;
    }
}