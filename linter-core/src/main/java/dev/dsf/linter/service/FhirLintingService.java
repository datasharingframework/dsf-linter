package dev.dsf.linter.service;

import dev.dsf.linter.fhir.FhirResourceLinter;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.util.resource.ResourceResolver;

import java.io.File;
import java.util.List;

/**
 * REFACTORED: FHIR linting service that extends AbstractResourceLintingService.
 * <p>
 * This refactored version eliminates ~250 lines of duplicated code by inheriting
 * common linting operations from the abstract base class.
 * </p>
 *
 * @since 1.1.0
 */
public class FhirLintingService extends AbstractResourceLintingService<FhirLintItem> {

    private final FhirResourceLinter fhirResourceLinter;

    public FhirLintingService(Logger logger) {
        super(logger);
        this.fhirResourceLinter = new FhirResourceLinter(logger);
    }

    // IMPLEMENTATION OF ABSTRACT METHODS

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
        return new PluginDefinitionFhirFileReferencedButNotFoundLintItem(
                pluginName,
                LinterSeverity.ERROR,
                new File(missingRef),
                "Referenced FHIR file not found"
        );
    }

    @Override
    protected AbstractLintItem createOutsideRootLintItem(
            String pluginName,
            String reference,
            ResourceResolver.ResolutionResult result) {

        return new PluginDefinitionFhirFileReferencedFoundOutsideExpectedRootLintItem(
                pluginName,
                result.file().orElseThrow(),
                reference,
                result.expectedRoot(),
                result.actualLocation()
        );
    }

    @Override
    protected AbstractLintItem createResourceSuccessItem(
            String pluginName,
            File resourceFile,
            LintingOutput output) {

        String fhirReference = extractFhirReference(output.LintItems());
        return new FhirElementLintItemSuccess(
                resourceFile,
                fhirReference,
                "Referenced FHIR file found and is readable."
        );
    }

    @Override
    protected AbstractLintItem createPluginSuccessItem(String pluginName, File resourceFile) {
        return new PluginDefinitionLintItemSuccess(
                resourceFile,
                pluginName,
                String.format("FHIR file '%s' successfully parsed and linted for plugin '%s'",
                        resourceFile.getName(), pluginName)
        );
    }

    @Override
    protected boolean isUnparsableItem(AbstractLintItem item) {
        return item instanceof PluginDefinitionUnparsableFhirResourceLintItem;
    }

    // FHIR-SPECIFIC HELPER METHODS

    /**
     * Extracts FHIR reference from lint items.
     */
    private String extractFhirReference(List<AbstractLintItem> items) {
        return items.stream()
                .filter(item -> item instanceof FhirElementLintItem)
                .map(item -> ((FhirElementLintItem) item).getFhirReference())
                .filter(ref -> ref != null && !ref.isBlank())
                .findFirst()
                .orElse("unknown_reference");
    }
}