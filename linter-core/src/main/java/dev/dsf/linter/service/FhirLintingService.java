package dev.dsf.linter.service;

import dev.dsf.linter.fhir.FhirResourceLinter;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.output.item.FhirElementLintItem;
import dev.dsf.linter.output.item.PluginLintItem;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.util.resource.ResourceResolutionResult;

import java.io.File;
import java.util.List;

/**
 * FHIR linting service that extends AbstractResourceLintingService.
 *
 * @since 1.1.0
 */
public class FhirLintingService extends AbstractResourceLintingService {

    private final FhirResourceLinter fhirResourceLinter;

    public FhirLintingService(Logger logger) {
        super(logger);
        this.fhirResourceLinter = new FhirResourceLinter(logger);
    }

    @Override
    protected String getResourceTypeName() {
        return "FHIR";
    }

    @Override
    protected LintingOutput lintSingleFile(String pluginName, File resourceFile) {
        return fhirResourceLinter.lintSingleFile(resourceFile.toPath());
    }

    @Override
    protected AbstractLintItem createMissingReferenceLintItem(String pluginName, String missingRef) {
        return new PluginLintItem(
                LinterSeverity.ERROR,
                LintingType.PLUGIN_DEFINITION_FHIR_RESOURCE_NOT_FOUND,
                new File(missingRef),
                pluginName,
                "Referenced FHIR file not found"
        );
    }

    @Override
    protected AbstractLintItem createOutsideRootLintItem(
            String pluginName,
            String reference,
            ResourceResolutionResult result) {

        return new PluginLintItem(
                LinterSeverity.WARN,
                LintingType.PLUGIN_DEFINITION_FHIR_FILE_OUTSIDE_ROOT,
                result.file().orElseThrow(),
                pluginName,
                String.format("FHIR file '%s' found outside expected root '%s' at '%s'",
                        reference, result.expectedRoot(), result.actualLocation())
        );
    }

    @Override
    protected AbstractLintItem createResourceSuccessItem(
            String pluginName,
            File resourceFile,
            LintingOutput output) {

        String fhirReference = extractFhirReference(output.LintItems());
        return FhirElementLintItem.success(resourceFile, fhirReference,
                "Referenced FHIR file found and is readable.");
    }

    @Override
    protected AbstractLintItem createPluginSuccessItem(String pluginName, File resourceFile) {
        return PluginLintItem.success(resourceFile, pluginName,
                String.format("FHIR file '%s' successfully parsed and linted for plugin '%s'",
                        resourceFile.getName(), pluginName));
    }

    @Override
    protected boolean isUnparsableItem(AbstractLintItem item) {
        return item instanceof PluginLintItem pi &&
               pi.getType() == LintingType.PLUGIN_DEFINITION_UNPARSABLE_FHIR_RESOURCE;
    }

    private String extractFhirReference(List<AbstractLintItem> items) {
        return items.stream()
                .filter(item -> item instanceof FhirElementLintItem)
                .map(item -> ((FhirElementLintItem) item).getFhirReference())
                .filter(ref -> ref != null && !ref.isBlank())
                .findFirst()
                .orElse("unknown_reference");
    }
}
