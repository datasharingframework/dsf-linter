package dev.dsf.linter.fhir;

import dev.dsf.linter.exception.ResourceValidationException;
import dev.dsf.linter.item.PluginDefinitionUnparsableFhirResourceValidationItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.util.validation.ValidationOutput;
import dev.dsf.linter.item.FhirElementValidationItem;
import dev.dsf.linter.util.validation.AbstractFhirInstanceValidator;
import dev.dsf.linter.util.converter.JsonXmlConverter;
import org.w3c.dom.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static dev.dsf.linter.util.ValidationUtils.getFile;

/**
 * Validates FHIR resources by aggregating concrete {@link AbstractFhirInstanceValidator} implementations
 * and delegating validation to the first matching validator based on resource type.
 */
public final class FhirResourceValidator {
    /**
     * All discovered validators (cached).
     */
    private final List<AbstractFhirInstanceValidator> validators;

    private final Logger logger;

    public FhirResourceValidator(Logger logger) {
        this.logger = logger;
        // Discover validators via ServiceLoader OR fallback to manual list
        ServiceLoader<AbstractFhirInstanceValidator> loader =
                ServiceLoader.load(AbstractFhirInstanceValidator.class);
        validators = loader.stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toCollection(ArrayList::new));

        // Fallback in case ServiceLoader finds nothing (e.g., during tests)
        if (validators.isEmpty()) {
            validators.add(
                    new FhirActivityDefinitionValidator()
            );
            validators.add(
                    new FhirTaskValidator()
            );
            validators.add(
                    new FhirValueSetValidator()
            );
            validators.add(
                    new FhirCodeSystemValidator()
            );
            validators.add(
                    new FhirQuestionnaireValidator()
            );
            validators.add(
                    new FhirStructureDefinitionValidator()
            );
        }
    }

    /*
      Public API
       */


    /**
     * Validates a single FHIR XML or JSON resource file and returns its validation output.
     * If parsing fails, it returns a ValidationOutput with an error item instead of throwing an exception.
     */
    public ValidationOutput validateSingleFile(Path fhirFile) {
        try {
            List<FhirElementValidationItem> issues = validateFileInternal(fhirFile.toFile());
            return new ValidationOutput(new ArrayList<>(issues));
        } catch (ResourceValidationException e) {
            String pluginName = getProjectRoot(fhirFile).getName();
            PluginDefinitionUnparsableFhirResourceValidationItem errorItem = getPluginDefinitionUnparsableFhirResourceValidationItem(fhirFile, pluginName);
            return new ValidationOutput(Collections.singletonList(errorItem));
        }
    }

    private static PluginDefinitionUnparsableFhirResourceValidationItem getPluginDefinitionUnparsableFhirResourceValidationItem(Path fhirFile, String pluginName) {
        String fileName = fhirFile.getFileName().toString();
        String errorMessage = String.format(
                "Validation for plugin \"%s\" may has some false items because the file \"%s\" is unparsable.",
                pluginName,
                fileName
        );

        return new PluginDefinitionUnparsableFhirResourceValidationItem(
                fhirFile.toFile(),
                pluginName,
                errorMessage
        );
    }

    /*
      Internal helpers
      */

    private List<FhirElementValidationItem> validateFileInternal(File file) throws ResourceValidationException
    {
        List<FhirElementValidationItem> issues = new ArrayList<>();
        Document doc;

        try
        {
            if (file.getName().toLowerCase().endsWith(".xml"))
            {
                doc = parseXmlSafe(file);
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
        catch (FhirParsingException e)
        {
            // Requirement 7: Abort on syntax error by converting internal to public exception
            throw new ResourceValidationException("FHIR resource parsing failed", file.toPath(), e);
        }

        if (doc == null)
            return issues;

        for (AbstractFhirInstanceValidator v : validators)
        {
            if (v.canValidate(doc))
            {
                @SuppressWarnings("unchecked")
                List<FhirElementValidationItem> found = (List<FhirElementValidationItem>) v.validate(doc, file);
                issues.addAll(found);
                return issues;
            }
        }

        logger.info("[INFO] No FHIR validator recognized file: " + file.getName());
        return issues;
    }

    /**
     * Parses a JSON FHIR file and converts it to a DOM Document for validation.
     * This allows the existing DOM-based validators to work with JSON files without modification.
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

    private Document parseXmlSafe(File file)
    {
        try (InputStream in = Files.newInputStream(file.toPath()))
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(in);
        }
        catch (Exception e)
        {
            // Throw internal exception instead of returning null
            throw new FhirParsingException("Failed to parse XML", e);
        }
    }

    private File getProjectRoot(Path filePath) {
        return getFile(filePath);
    }


}