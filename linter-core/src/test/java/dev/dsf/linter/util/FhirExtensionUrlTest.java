package dev.dsf.linter.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dsf.linter.util.converter.JsonXmlConverter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for verifying the correct conversion of {@code extension.url} properties
 * from FHIR JSON resources into XML. These tests use the centralized
 * {@link JsonXmlConverter#convertJsonToXml(JsonNode)} method.
 *
 * <p>Specifically, they confirm that {@code extension.url} values:</p>
 * <ul>
 *   <li>Are represented as XML attributes (e.g. {@code <extension url="..." />})</li>
 *   <li>Are not incorrectly rendered as child elements (e.g. {@code <extension><url ... /></extension>})</li>
 *   <li>Work correctly for both top-level and nested extension structures</li>
 * </ul>
 */
public class FhirExtensionUrlTest
{
    /**
     * Verifies that a single FHIR {@code extension} element with a {@code url} property
     * is correctly converted to an XML element with the {@code url} as an attribute.
     *
     * <p>If the conversion is incorrect (e.g. the {@code url} appears as a child element),
     * the test will fail with a descriptive message.</p>
     *
     * @throws Exception if parsing or conversion fails
     */
    @Test
    void testExtensionUrlHandling() throws Exception
    {
        String jsonContent = """
            {
              "resourceType": "Task",
              "extension": [
                {
                  "url": "http://dsf.dev/fhir/StructureDefinition/extension-process-authorization",
                  "extension": [
                    {
                      "url": "message-name",
                      "valueString": "testMessage"
                    }
                  ]
                }
              ]
            }""";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonContent);

        // Use the utility class instead of reflection
        String xmlResult = JsonXmlConverter.convertJsonToXml(jsonNode);

        System.out.println("Generated XML:");
        System.out.println(xmlResult);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource(new StringReader(xmlResult)));

        assertTrue(xmlResult.contains("extension"), "Should contain extension element");

        boolean hasUrlAsChildElement =
                xmlResult.contains("<url value=\"http://dsf.dev/fhir/StructureDefinition/extension-process-authorization\"/>");

        boolean hasUrlAsAttribute =
                xmlResult.contains("<extension url=\"http://dsf.dev/fhir/StructureDefinition/extension-process-authorization\">");

        if (hasUrlAsChildElement && !hasUrlAsAttribute)
        {
            System.out.println("CURRENT BEHAVIOR (INCORRECT): URL is a child element");
            fail("The fix should have been applied - URL should be an attribute, not a child element");
        }
        else if (hasUrlAsAttribute && !hasUrlAsChildElement)
        {
            System.out.println("FIXED BEHAVIOR (CORRECT): URL is an attribute");
            assertTrue(hasUrlAsAttribute, "Fixed implementation should have URL as attribute");
            assertFalse(hasUrlAsChildElement, "Fixed implementation should not have URL as child element");
        }
        else
        {
            fail("Unexpected XML structure in generated output");
        }
    }

    /**
     * Verifies the correct transformation of complex nested FHIR {@code extension} elements.
     * This test ensures that:
     * <ul>
     *   <li>Both parent and nested extensions have {@code url} values as attributes</li>
     *   <li>No {@code <url>} child elements exist</li>
     *   <li>{@code valueString} and {@code valueCanonical} remain as XML child elements</li>
     * </ul>
     *
     * @throws Exception if conversion or XML parsing fails
     */
    @Test
    void testNestedExtensionsWithUrls() throws Exception
    {
        String jsonContent = """
            {
              "resourceType": "ActivityDefinition",
              "extension": [
                {
                  "url": "http://dsf.dev/fhir/StructureDefinition/extension-process-authorization",
                  "extension": [
                    {
                      "url": "message-name",
                      "valueString": "testMessage"
                    },
                    {
                      "url": "task-profile",
                      "valueCanonical": "http://dsf.dev/fhir/StructureDefinition/dsf-task-base"
                    }
                  ]
                },
                {
                  "url": "http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-parent",
                  "valueString": "organizationA"
                }
              ]
            }""";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonContent);

        // Use the utility class instead of reflection
        String xmlResult = JsonXmlConverter.convertJsonToXml(jsonNode);

        System.out.println("Generated XML for nested extensions:");
        System.out.println(xmlResult);

        // Verify parent extensions have url attributes
        assertTrue(xmlResult.contains("<extension url=\"http://dsf.dev/fhir/StructureDefinition/extension-process-authorization\">"),
                "Parent extension should have URL as attribute");
        assertTrue(xmlResult.contains("<extension url=\"http://dsf.dev/fhir/StructureDefinition/extension-process-authorization-parent\">"),
                "Second parent extension should have URL as attribute");

        // Verify nested extensions have url attributes
        assertTrue(xmlResult.contains("<extension url=\"message-name\">"),
                "Nested extension 'message-name' should have URL as attribute");
        assertTrue(xmlResult.contains("<extension url=\"task-profile\">"),
                "Nested extension 'task-profile' should have URL as attribute");

        // Ensure no <url> child elements exist
        assertFalse(xmlResult.contains("<url value="),
                "Should not contain any URL elements as child elements");

        // Confirm value elements are preserved correctly
        assertTrue(xmlResult.contains("<valueString value=\"testMessage\"/>"),
                "Should contain valueString as child element");
        assertTrue(xmlResult.contains("<valueCanonical value=\"http://dsf.dev/fhir/StructureDefinition/dsf-task-base\"/>"),
                "Should contain valueCanonical as child element");
        assertTrue(xmlResult.contains("<valueString value=\"organizationA\"/>"),
                "Should contain parent extension valueString as child element");
    }
}
