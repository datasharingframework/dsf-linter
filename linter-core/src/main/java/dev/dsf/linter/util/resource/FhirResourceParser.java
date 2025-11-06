package dev.dsf.linter.util.resource;

import dev.dsf.linter.util.converter.JsonXmlConverter;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
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

        try (InputStream in = Files.newInputStream(filePath)) {
            return parseFhirResource(in, filePath.getFileName().toString());
        }
    }

    public static Document parseXml(Path filePath) throws Exception {
        try (InputStream in = Files.newInputStream(filePath)) {
            return parseXml(in);
        }
    }

    public static Document parseJsonToXml(Path filePath) throws Exception {
        try (InputStream in = Files.newInputStream(filePath)) {
            return parseJsonToXml(in);
        }
    }

    public static Document parseXml(InputStream inputStream) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(inputStream);
    }

    public static Document parseJsonToXml(InputStream inputStream) throws Exception {
        return getDocument(inputStream);
    }

    static Document getDocument(InputStream inputStream) throws Exception {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonNode =
                    mapper.readTree(inputStream);

            String xmlString = JsonXmlConverter.convertJsonToXml(jsonNode);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new org.xml.sax.InputSource(
                    new java.io.StringReader(xmlString)));
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new UnsupportedOperationException(
                    "JSON parsing requires Jackson library on classpath", e);
        }
    }

    public static Document parseFhirResource(InputStream inputStream, String fileName)
            throws Exception {
        String lowerName = fileName.toLowerCase();

        if (lowerName.endsWith(".xml")) {
            return parseXml(inputStream);
        } else if (lowerName.endsWith(".json")) {
            return parseJsonToXml(inputStream);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + fileName +
                            " (only .xml and .json are supported)");
        }
    }
}