package dev.dsf.utils.validator;

import dev.dsf.utils.validator.util.converter.JsonXmlConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for verifying the correct handling of {@code extension.url} properties
 * during FHIR JSON to XML conversion. These tests use the centralized
 * {@link JsonXmlConverter#convertJsonToXml(JsonNode)} method.
 *
 * <p>Key goals of these tests include:</p>
 * <ul>
 *   <li>Ensuring {@code extension.url} is rendered as an XML attribute</li>
 *   <li>Ensuring normal {@code url} elements outside of extensions are not altered</li>
 *   <li>Verifying behavior for extensions that omit the {@code url} property</li>
 * </ul>
 */
public class FhirExtensionUrlConversionTest
{
    /**
     * Object mapper for parsing JSON strings into Jackson trees.
     */
    private ObjectMapper objectMapper;

    /**
     * Initializes the object mapper for JSON parsing.
     */
    @BeforeEach
    void setUp()
    {
        objectMapper = new ObjectMapper();
    }

    /**
     * Tests that {@code extension.url} properties are converted to attributes in XML.
     *
     * <p>This includes nested extensions, ensuring that their {@code url} values also
     * appear as attributes rather than child elements.</p>
     *
     * @throws Exception if parsing or conversion fails
     */
    @Test
    void testExtensionUrlConversionToAttribute() throws Exception
    {
        String jsonInput = """
            {
              "resourceType": "Task",
              "id": "test-task",
              "extension": [
                {
                  "url": "http://dsf.dev/fhir/StructureDefinition/extension-process-authorization",
                  "extension": [
                    {
                      "url": "message-name",
                      "valueString": "example-message"
                    },
                    {
                      "url": "task-profile",
                      "valueCanonical": "http://dsf.dev/fhir/StructureDefinition/task-example"
                    }
                  ]
                }
              ]
            }""";

        JsonNode jsonNode = objectMapper.readTree(jsonInput);
        // Use the utility class instead of reflection
        String xmlOutput = JsonXmlConverter.convertJsonToXml(jsonNode);

        System.out.println("Generated XML:");
        System.out.println(xmlOutput);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new org.xml.sax.InputSource(new StringReader(xmlOutput)));

        XPath xpath = XPathFactory.newInstance().newXPath();

        // Validate main extension
        NodeList mainExtensions = (NodeList) xpath.evaluate(
                "//*[local-name()='extension' and @url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization']",
                doc, XPathConstants.NODESET);
        assertEquals(1, mainExtensions.getLength(),
                "Should find exactly one extension element with the main URL as attribute");

        Element mainExtension = (Element) mainExtensions.item(0);
        assertEquals("http://dsf.dev/fhir/StructureDefinition/extension-process-authorization",
                mainExtension.getAttribute("url"));

        // Validate nested extensions
        NodeList nestedExtensions = (NodeList) xpath.evaluate(
                "//*[local-name()='extension' and @url='message-name']",
                doc, XPathConstants.NODESET);
        assertEquals(1, nestedExtensions.getLength(),
                "Should find exactly one nested extension with 'message-name' URL as attribute");

        NodeList taskProfileExtensions = (NodeList) xpath.evaluate(
                "//*[local-name()='extension' and @url='task-profile']",
                doc, XPathConstants.NODESET);
        assertEquals(1, taskProfileExtensions.getLength(),
                "Should find exactly one nested extension with 'task-profile' URL as attribute");

        // Ensure no <url> elements exist
        NodeList urlElements = (NodeList) xpath.evaluate(
                "//*[local-name()='url']",
                doc, XPathConstants.NODESET);
        assertEquals(0, urlElements.getLength(),
                "Should NOT find any <url> child elements - URLs should be attributes");
    }

    /**
     * Tests that FHIR resources such as {@code ValueSet} retain their standard
     * {@code url} elements as XML child elements and not attributes.
     *
     * <p>This ensures that the special URL-to-attribute logic is only applied
     * to {@code extension} elements.</p>
     *
     * @throws Exception if parsing or conversion fails
     */
    @Test
    void testNonExtensionElementsUnchanged() throws Exception
    {
        String jsonInput = """
            {
              "resourceType": "ValueSet",
              "id": "test-valueset",
              "url": "http://dsf.dev/fhir/ValueSet/test",
              "name": "TestValueSet",
              "compose": {
                "include": [
                  {
                    "system": "http://dsf.dev/fhir/CodeSystem/test",
                    "concept": [
                      {
                        "code": "test-code"
                      }
                    ]
                  }
                ]
              }
            }""";

        JsonNode jsonNode = objectMapper.readTree(jsonInput);
        // Use the utility class instead of reflection
        String xmlOutput = JsonXmlConverter.convertJsonToXml(jsonNode);

        System.out.println("Generated XML for non-extension:");
        System.out.println(xmlOutput);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new org.xml.sax.InputSource(new StringReader(xmlOutput)));

        XPath xpath = XPathFactory.newInstance().newXPath();

        // Validate that url is a child element, not attribute
        NodeList urlElements = (NodeList) xpath.evaluate(
                "//*[local-name()='url' and @value='http://dsf.dev/fhir/ValueSet/test']",
                doc, XPathConstants.NODESET);
        assertEquals(1, urlElements.getLength(),
                "Should find the url element as a child with value attribute (normal FHIR behavior)");

        NodeList valueSetElements = (NodeList) xpath.evaluate(
                "//*[local-name()='ValueSet']",
                doc, XPathConstants.NODESET);
        assertEquals(1, valueSetElements.getLength(), "Should find the ValueSet root element");

        Element valueSetElement = (Element) valueSetElements.item(0);
        assertFalse(valueSetElement.hasAttribute("url"),
                "ValueSet element should NOT have url as attribute");
    }

    /**
     * Tests that {@code extension} elements without a {@code url} property
     * do not produce a {@code url} attribute in the resulting XML.
     *
     * <p>This validates that optional fields are respected during transformation.</p>
     *
     * @throws Exception if parsing or conversion fails
     */
    @Test
    void testExtensionWithoutUrlUnchanged() throws Exception
    {
        String jsonInput = """
            {
              "resourceType": "Task",
              "id": "test-task",
              "extension": [
                {
                  "valueString": "some-value"
                }
              ]
            }""";

        JsonNode jsonNode = objectMapper.readTree(jsonInput);
        // Use the utility class instead of reflection
        String xmlOutput = JsonXmlConverter.convertJsonToXml(jsonNode);

        System.out.println("Generated XML for extension without URL:");
        System.out.println(xmlOutput);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new org.xml.sax.InputSource(new StringReader(xmlOutput)));

        XPath xpath = XPathFactory.newInstance().newXPath();

        NodeList extensionElements = (NodeList) xpath.evaluate(
                "//*[local-name()='extension']",
                doc, XPathConstants.NODESET);
        assertEquals(1, extensionElements.getLength(), "Should find one extension element");

        Element extensionElement = (Element) extensionElements.item(0);
        assertFalse(extensionElement.hasAttribute("url"),
                "Extension without url property should NOT have url attribute");

        NodeList valueStringElements = (NodeList) xpath.evaluate(
                "//*[local-name()='valueString' and @value='some-value']",
                doc, XPathConstants.NODESET);
        assertEquals(1, valueStringElements.getLength(),
                "Should find valueString element with correct value");
    }
}
