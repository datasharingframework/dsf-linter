package dev.dsf.utils.validator.fhir;

import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.util.ValidationOutput;
import dev.dsf.utils.validator.item.FhirElementValidationItem;
import dev.dsf.utils.validator.util.AbstractFhirInstanceValidator;
import dev.dsf.utils.validator.util.JsonXmlConverter;
import dev.dsf.utils.validator.util.FhirFileUtils;
import org.w3c.dom.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Aggregates all concrete {@link AbstractFhirInstanceValidator} implementations and
 * delegates validation of FHIR XML files to the first matching validator.
 *
 * <p>Compared to the previous implementation the class has been streamlined:</p>
 * <ul>
 *   <li> Validators are now discovered lazily through {@link ServiceLoader} to avoid
 *       manual maintenance of the list in this class.</li>
 *   <li> Uses {@link java.nio.file.Files#walk} & JavaStreams for concise IO logic.</li>
 * </ul>
 */
public final class FhirResourceValidator {
    /**
     * All discovered validators (cached).
     */
    private final List<AbstractFhirInstanceValidator> validators;

    public FhirResourceValidator() {
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
     */
    public ValidationOutput validateSingleFile(Path fhirFile) throws ResourceValidationException
    {
        List<FhirElementValidationItem> issues = validateFileInternal(fhirFile.toFile());
        return new ValidationOutput(new ArrayList<>(issues));
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

        System.out.println("[DEBUG] No FHIR validator recognized file: " + file.getName());
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


}
