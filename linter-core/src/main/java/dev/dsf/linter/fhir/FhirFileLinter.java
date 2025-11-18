package dev.dsf.linter.fhir;

import dev.dsf.linter.exception.ResourceLinterException;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.item.FhirElementLintItem;
import dev.dsf.linter.output.item.PluginDefinitionUnparsableFhirResourceLintItem;
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
 * It handles the initial parsing of the file and then delegates the content
 * linting to the appropriate {@link AbstractFhirInstanceLinter} implementation.
 * <p>
 * This class is designed to be a self-contained utility.
 * </p>
 */
public class FhirFileLinter {

    private final List<AbstractFhirInstanceLinter> linters;
    private final Logger logger;

    public FhirFileLinter(List<AbstractFhirInstanceLinter> linters, Logger logger) {
        this.linters = linters;
        this.logger = logger;
    }

    /**
     * Lints a single FHIR XML or JSON resource file and returns its linting output.
     * If parsing fails, it returns a LintingOutput with an error item instead of throwing an exception.
     *
     * @param fhirFile the path to the FHIR file
     * @return a LintingOutput containing all lint issues
     */
    public LintingOutput lintSingleFile(Path fhirFile) {
        try {
            List<FhirElementLintItem> issues = lintFileInternal(fhirFile.toFile());
            return new LintingOutput(new ArrayList<>(issues));
        } catch (ResourceLinterException e) {
            String pluginName = LintingUtils.getProjectRoot(fhirFile).getName();
            PluginDefinitionUnparsableFhirResourceLintItem errorItem = 
                    createUnparsableResourceLintItem(fhirFile, pluginName);
            return new LintingOutput(Collections.singletonList(errorItem));
        }
    }

    /**
     * Internal method to lint a FHIR file by parsing it and delegating to the appropriate linter.
     *
     * @param file the FHIR file to lint
     * @return list of lint items found
     * @throws ResourceLinterException if parsing fails
     */
    private List<FhirElementLintItem> lintFileInternal(File file) throws ResourceLinterException {
        List<FhirElementLintItem> issues = new ArrayList<>();
        Document doc;

        try {
            // Use the unified FhirResourceParser for both XML and JSON
            doc = FhirResourceParser.parseFhirFile(file.toPath());
        } catch (Exception e) {
            // Abort on syntax error by converting to public exception
            throw new ResourceLinterException("FHIR resource parsing failed", file.toPath(), e);
        }

        if (doc == null) {
            return issues;
        }

        // Find the first matching linter and delegate
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

    /**
     * Creates a lint item for an unparsable FHIR resource.
     *
     * @param fhirFile the file that could not be parsed
     * @param pluginName the plugin name
     * @return lint item representing the parsing failure
     */
    private static PluginDefinitionUnparsableFhirResourceLintItem createUnparsableResourceLintItem(
            Path fhirFile, String pluginName) {
        String fileName = fhirFile.getFileName().toString();
        String errorMessage = String.format(
                "linting for plugin \"%s\" may has some false items because the file \"%s\" is unparsable.",
                pluginName,
                fileName
        );

        return new PluginDefinitionUnparsableFhirResourceLintItem(
                fhirFile.toFile(),
                pluginName,
                errorMessage
        );
    }
}

