package dev.dsf.linter.fhir;

import dev.dsf.linter.exception.ResourceLinterException;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.FhirElementLintItem;
import dev.dsf.linter.output.item.PluginLintItem;
import dev.dsf.linter.util.linting.AbstractFhirInstanceLinter;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.util.linting.LintingUtils;
import dev.dsf.linter.util.resource.FhirResourceParser;
import org.w3c.dom.Document;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FhirFileLinter is the primary entry point for linting a single FHIR file.
 */
public class FhirFileLinter {

    private final List<AbstractFhirInstanceLinter> linters;
    private final Logger logger;

    public FhirFileLinter(List<AbstractFhirInstanceLinter> linters, Logger logger) {
        this.linters = linters;
        this.logger = logger;
    }

    public LintingOutput lintSingleFile(Path fhirFile) {
        try {
            List<FhirElementLintItem> issues = lintFileInternal(fhirFile.toFile());
            return new LintingOutput(new ArrayList<>(issues));
        } catch (ResourceLinterException e) {
            String pluginName = LintingUtils.getProjectRoot(fhirFile).getName();
            PluginLintItem errorItem = createUnparsableResourceLintItem(fhirFile, pluginName);
            return new LintingOutput(Collections.singletonList(errorItem));
        }
    }

    private List<FhirElementLintItem> lintFileInternal(File file) throws ResourceLinterException {
        List<FhirElementLintItem> issues = new ArrayList<>();
        Document doc;

        try {
            doc = FhirResourceParser.parseFhirFile(file.toPath());
        } catch (Exception e) {
            throw new ResourceLinterException("FHIR resource parsing failed", file.toPath(), e);
        }

        if (doc == null) {
            return issues;
        }

        for (AbstractFhirInstanceLinter linter : linters) {
            if (linter.canLint(doc)) {
                @SuppressWarnings("unchecked")
                List<FhirElementLintItem> found = (List<FhirElementLintItem>) linter.lint(doc, file);
                issues.addAll(found);
                return issues;
            }
        }

        logger.info("[INFO] No FHIR linter recognized file: " + file.getName());
        return issues;
    }

    private static PluginLintItem createUnparsableResourceLintItem(Path fhirFile, String pluginName) {
        String fileName = fhirFile.getFileName().toString();
        String errorMessage = String.format(
                "linting for plugin \"%s\" may has some false items because the file \"%s\" is unparsable.",
                pluginName,
                fileName
        );

        return new PluginLintItem(
                LinterSeverity.ERROR,
                LintingType.PLUGIN_DEFINITION_UNPARSABLE_FHIR_RESOURCE,
                fhirFile.toFile(),
                pluginName,
                errorMessage
        );
    }
}
