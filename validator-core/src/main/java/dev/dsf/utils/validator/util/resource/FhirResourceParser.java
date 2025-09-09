package dev.dsf.utils.validator.util.resource;

import dev.dsf.utils.validator.util.converter.JsonXmlConverter;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.nio.file.Path;

/**
 * Utility class for parsing FHIR resource files into DOM documents.
 * Supports both XML and JSON file formats.
 */
public class FhirResourceParser {

    /**
     * Parses either an XML or JSON FHIR file into a DOM Document.
     * For JSON files, converts them to XML first.
     *
     * @param filePath the path to the XML or JSON file
     * @return a Document representing the parsed content, or null if an error occurs
     * @throws Exception if there is an I/O or parsing error
     */
    public static Document parseFhirFile(Path filePath) throws Exception {
        String fileName = filePath.toString().toLowerCase();

        if (fileName.endsWith(".xml")) {
            return parseXml(filePath);
        } else if (fileName.endsWith(".json")) {
            return parseJsonToXml(filePath);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileName +
                    " (only .xml and .json are supported)");
        }
    }

    /**
     * Parses an XML file at the given filePath into a Document object.
     * This method is namespace-aware and uses a DocumentBuilder to load and parse
     * the XML content.
     *
     * @param filePath the path to the XML file
     * @return a Document representing the parsed XML, or null if an error occurs
     * @throws Exception if there is an I/O or parsing error
     */
    public static Document parseXml(Path filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(fis);
        }
    }

    /**
     * Parses a JSON FHIR file and converts it to a DOM Document by first converting to XML.
     * Uses the same JSON-to-XML conversion logic as FhirResourceValidator.
     *
     * @param filePath the path to the JSON file
     * @return a Document representing the parsed content
     * @throws Exception if there is an I/O or parsing error
     */
    public static Document parseJsonToXml(Path filePath) throws Exception {
        try {
            // Use Jackson to parse JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonNode =
                    mapper.readTree(filePath.toFile());

            // Convert JSON to XML string using utility class
            String xmlString = JsonXmlConverter.convertJsonToXml(jsonNode);

            // Parse the XML string to create a DOM Document
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new org.xml.sax.InputSource(
                    new java.io.StringReader(xmlString)));
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Jackson not available, throw more specific exception
            throw new UnsupportedOperationException(
                    "JSON parsing requires Jackson library on classpath", e);
        }
    }
}