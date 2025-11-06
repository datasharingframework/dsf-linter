package dev.dsf.linter.fhir;

import dev.dsf.linter.exception.ResourceLinterException;
import dev.dsf.linter.output.item.PluginDefinitionUnparsableFhirResourceLintItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.util.linting.AbstractFhirInstanceLinter;
import dev.dsf.linter.util.linting.LintingOutput;
import dev.dsf.linter.output.item.FhirElementLintItem;
import dev.dsf.linter.util.converter.JsonXmlConverter;
import dev.dsf.linter.util.resource.FhirResourceParser;
import org.w3c.dom.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static dev.dsf.linter.util.linting.LintingUtils.getProjectRoot;


/**
 * lints FHIR resources by aggregating concrete {@link AbstractFhirInstanceLinter} implementations
 * and delegating linting to the first matching linter based on resource type.
 */
public final class FhirResourceLinter {
    /**
     * All discovered linters (cached).
     */
    private final List<AbstractFhirInstanceLinter> linters;

    private final Logger logger;

    public FhirResourceLinter(Logger logger) {
        this.logger = logger;
        // Discover linters via ServiceLoader OR fallback to manual list
        ServiceLoader<AbstractFhirInstanceLinter> loader =
                ServiceLoader.load(AbstractFhirInstanceLinter.class);
        linters = loader.stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toCollection(ArrayList::new));

        // Fallback in case ServiceLoader finds nothing (e.g., during tests)
        if (linters.isEmpty()) {
            linters.add(
                    new FhirActivityDefinitionLinter()
            );
            linters.add(
                    new FhirTaskLinter()
            );
            linters.add(
                    new FhirValueSetLinter()
            );
            linters.add(
                    new FhirCodeSystemLinter()
            );
            linters.add(
                    new FhirQuestionnaireLinter()
            );
            linters.add(
                    new FhirStructureDefinitionLinter()
            );
        }
    }

    /*
      Public API
       */


    /**
     * lints a single FHIR XML or JSON resource file and returns its linting output.
     * If parsing fails, it returns a LintingOutput with an error item instead of throwing an exception.
     */
    public LintingOutput lintSingleFile(Path fhirFile) {
        try {
            List<FhirElementLintItem> issues = lintFileInternal(fhirFile.toFile());
            return new LintingOutput(new ArrayList<>(issues));
        } catch (ResourceLinterException e) {
            String pluginName = getProjectRoot(fhirFile).getName();
            PluginDefinitionUnparsableFhirResourceLintItem errorItem = getPluginDefinitionUnparsableFhirResourceLintItem(fhirFile, pluginName);
            return new LintingOutput(Collections.singletonList(errorItem));
        }
    }

    private static PluginDefinitionUnparsableFhirResourceLintItem getPluginDefinitionUnparsableFhirResourceLintItem(Path fhirFile, String pluginName) {
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

    /*
      Internal helpers
      */

    private List<FhirElementLintItem> lintFileInternal(File file) throws ResourceLinterException
    {
        List<FhirElementLintItem> issues = new ArrayList<>();
        Document doc;

        try
        {
            if (file.getName().toLowerCase().endsWith(".xml"))
            {
                doc = FhirResourceParser.parseXml(file.toPath());
            }
            else if (file.getName().toLowerCase().endsWith(".json"))
            {
                doc = parseJsonSafe(file);
            }
            else
            {
                return issues; // Should not happen with proper filtering
            }
        }
        catch (Exception e)
        {
            // Requirement 7: Abort on syntax error by converting internal to public exception
            throw new ResourceLinterException("FHIR resource parsing failed", file.toPath(), e);
        }

        if (doc == null)
            return issues;

        for (AbstractFhirInstanceLinter l : linters)
        {
            if (l.canLint(doc))
            {
                @SuppressWarnings("unchecked")
                List<FhirElementLintItem> found = (List<FhirElementLintItem>) l.lint(doc, file);
                issues.addAll(found);
                return issues;
            }
        }

        logger.info("[INFO] No FHIR linter recognized file: " + file.getName());
        return issues;
    }

    /**
     * Parses a JSON FHIR file and converts it to a DOM Document for linting.
     * This allows the existing DOM-based linters to work with JSON files without modification.
     */
    private Document parseJsonSafe(File file)
    {
        try
        {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(file);
            String xmlString = JsonXmlConverter.convertJsonToXml(jsonNode);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new InputSource(new StringReader(xmlString)));
        }
        catch (Exception e)
        {
            // Throw internal exception instead of returning null
            throw new FhirParsingException("Failed to parse/convert JSON", e);
        }
    }

    /**
     * Package-private method for testing XML conversion.
     * This allows tests to inspect the generated XML.
     */
    public String convertJsonToXmlForTesting(String jsonContent) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonContent);
        return JsonXmlConverter.convertJsonToXml(jsonNode);
    }


    /**
     * Internal unchecked exception for parsing failures.
     */
    private static class FhirParsingException extends RuntimeException
    {
        public FhirParsingException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }

}