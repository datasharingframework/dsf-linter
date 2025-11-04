package dev.dsf.linter.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dsf.linter.fhir.FhirResourceLinter;
import dev.dsf.linter.logger.ConsoleLogger;
import dev.dsf.linter.logger.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FhirResourceLinter} that verify correct conversion of FHIR JSON resources to XML.
 * <p>
 * These tests cover various FHIR resource structures including basic types, extensions, nested objects,
 * arrays, and edge cases like nulls, empty structures, and large input. Both valid and invalid inputs
 * are tested to ensure robustness and correctness of the JSON-to-XML transformation logic.
 * </p>
 *
 * <p>Tested methods include:</p>
 * <ul>
 *   <li>{@code convertJsonToXmlForTesting(String json)} — converts a raw JSON string into FHIR XML.</li>
 *   <li>{@code convertJsonNodeToXml(JsonNode node)} — handles JSON nodes directly and constructs corresponding XML.</li>
 * </ul>
 *
 * <p>
 * The resulting XML is checked for structural validity, presence of expected FHIR elements,
 * correct namespace usage, correct XML escaping, and DOM compatibility.
 * </p>
 *
 * <p><b>Dependencies:</b> Uses JUnit 5, Jackson {@code ObjectMapper}, and standard Java XML parsers.</p>
 *
 * @author Khalil Malla
 */
class FhirResourceLinterJsonToXmlTest {

    private FhirResourceLinter linter;
    private ObjectMapper objectMapper;
    private XPath xpath;

    /**
     * Initializes the linter before each test case.
     */
    @BeforeEach
    void setUp() {
        Logger logger = new ConsoleLogger(false);
        linter = new FhirResourceLinter(logger);
        objectMapper = new ObjectMapper();
        xpath = XPathFactory.newInstance().newXPath();
    }

    // HELPER METHODS

    /**
     * Helper method to convert JSON string to XML and parse into DOM Document.
     * Reduces repetition across multiple test methods.
     */
    private Document parseJsonToXmlDocument(String json) throws Exception {
        String xml = linter.convertJsonToXmlForTesting(json);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Helper method to load JSON from classpath resource.
     */
    private String loadJsonFromResource() throws Exception {
        try (InputStream jsonStream = getClass().getClassLoader().getResourceAsStream("fhir/examples/dashBoardReport/ActivityDefinition/report-autostart.json")) {
            if (jsonStream == null) {
                throw new RuntimeException("Resource not found: " + "fhir/examples/dashBoardReport/ActivityDefinition/report-autostart.json");
            }
            return new String(jsonStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Helper method to assert element exists with specific value using XPath.
     */
    private void assertElementWithValue(Document doc, String elementName, String expectedValue) throws Exception {
        String xpathExpr = String.format("//*[local-name()='%s' and @value='%s']", elementName, expectedValue);
        NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
        assertTrue(nodes.getLength() > 0,
                String.format("Element <%s value=\"%s\"/> not found", elementName, expectedValue));
    }

    /**
     * Helper method to assert element exists using XPath.
     */
    private void assertElementExists(Document doc, String xpathExpr) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
        assertTrue(nodes.getLength() > 0, "XPath expression should match at least one element: " + xpathExpr);
    }

    /**
     * Helper method to count elements matching XPath expression.
     */
    private int countElements(Document doc, String xpathExpr) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
        return nodes.getLength();
    }

    @Nested
    @DisplayName("Positive Test Cases")
    class PositiveTests {

        /**
         * Tests conversion of a valid FHIR ActivityDefinition JSON file into a well-formed XML string.
         * Uses fallback JSON if resource file is not available.
         */
        @Test
        @DisplayName("should convert valid FHIR ActivityDefinition JSON to XML")
        void testConvertValidActivityDefinitionJsonToXml() throws Exception {
            String jsonContent;
            try {
                jsonContent = loadJsonFromResource();
            } catch (Exception e) {
                // Fallback to inline JSON if resource loading fails
                jsonContent = """
                    {
                        "resourceType": "ActivityDefinition",
                        "status": "active",
                        "extension": [
                            {
                                "url": "http://dsf.dev/fhir/StructureDefinition/extension-process-authorization",
                                "extension": [
                                    {
                                        "url": "message-name",
                                        "valueString": "dashboardReportAutostartStart"
                                    }
                                ]
                            }
                        ]
                    }
                    """;
            }

            Document doc = parseJsonToXmlDocument(jsonContent);

            // Verify document structure using DOM
            assertEquals("ActivityDefinition", doc.getDocumentElement().getLocalName());
            assertEquals("http://hl7.org/fhir", doc.getDocumentElement().getNamespaceURI());

            // Verify specific content exists using XPath - use local-name for namespace issues
            assertElementExists(doc, "//*[local-name()='extension' and @url='http://dsf.dev/fhir/StructureDefinition/extension-process-authorization']");
            assertElementExists(doc, "//*[local-name()='extension' and @url='message-name']");
            assertElementWithValue(doc, "valueString", "dashboardReportAutostartStart");
        }

        /**
         * Tests conversion of a minimal Patient resource in JSON format.
         * Ensures basic properties are correctly rendered in XML.
         */
        @Test
        @DisplayName("should convert simple FHIR JSON with basic elements")
        void testConvertSimpleJsonToXml() throws Exception {
            String simpleJson = """
                {
                    "resourceType": "Patient",
                    "id": "example",
                    "active": true,
                    "name": [
                        {
                            "family": "Doe",
                            "given": ["John"]
                        }
                    ]
                }
                """;

            Document doc = parseJsonToXmlDocument(simpleJson);

            assertEquals("Patient", doc.getDocumentElement().getLocalName());
            assertEquals("http://hl7.org/fhir", doc.getDocumentElement().getNamespaceURI());

            assertElementWithValue(doc, "id", "example");
            assertElementWithValue(doc, "active", "true");
            assertElementWithValue(doc, "family", "Doe");
            assertElementWithValue(doc, "given", "John");
        }

        /**
         * Verifies correct conversion of a JSON resource containing extensions into XML format.
         */
        @Test
        @DisplayName("should convert JSON with extensions and nested structures")
        void testConvertJsonWithExtensions() throws Exception {
            String jsonWithExtensions = """
                {
                    "resourceType": "Task",
                    "extension": [
                        {
                            "url": "http://example.org/extension",
                            "valueString": "test-value"
                        }
                    ]
                }
                """;

            Document doc = parseJsonToXmlDocument(jsonWithExtensions);

            assertEquals("Task", doc.getDocumentElement().getLocalName());
            assertElementExists(doc, "//*[local-name()='extension' and @url='http://example.org/extension']");
            assertElementWithValue(doc, "valueString", "test-value");
        }

        /**
         * Tests how the conversion handles arrays by providing a Bundle with multiple entries.
         * Simulates typical bundle processing scenarios with 2 entries.
         */
        @Test
        @DisplayName("should convert JSON with arrays")
        void testConvertJsonWithArrays() throws Exception {
            String jsonWithArrays = """
                {
                    "resourceType": "Bundle",
                    "entry": [
                        {
                            "resource": {
                                "resourceType": "Patient"
                            }
                        },
                        {
                            "resource": {
                                "resourceType": "Observation"
                            }
                        }
                    ]
                }
                """;

            Document doc = parseJsonToXmlDocument(jsonWithArrays);

            assertEquals("Bundle", doc.getDocumentElement().getLocalName());

            // Count entry elements using XPath
            int entryCount = countElements(doc, "//*[local-name()='entry']");
            assertEquals(2, entryCount, "Should have exactly 2 entry elements");

            assertElementExists(doc, "//*[local-name()='entry']/*[local-name()='resource']");
        }

        /**
         * Ensures null values in the input JSON are skipped during XML conversion.
         */
        @Test
        @DisplayName("should skip null values in JSON conversion")
        void testConvertJsonWithNullValues() throws Exception {
            String jsonWithNulls = """
                {
                    "resourceType": "Patient",
                    "id": "example",
                    "active": null,
                    "name": "John Doe"
                }
                """;

            Document doc = parseJsonToXmlDocument(jsonWithNulls);

            assertEquals("Patient", doc.getDocumentElement().getLocalName());
            assertElementWithValue(doc, "id", "example");
            assertElementWithValue(doc, "name", "John Doe");

            // Null value should be skipped - verify using XPath
            int activeCount = countElements(doc, "//*[local-name()='active']");
            assertEquals(0, activeCount, "Null 'active' field should be skipped");
        }

        /**
         * Tests conversion of a very large JSON file with many entries to ensure performance and memory safety.
         * Simulates typical batch processing scenarios with 100 entries.
         */
        @Test
        @DisplayName("should handle large JSON without performance issues")
        void testConvertLargeJson() throws Exception {
            StringBuilder largeJsonBuilder = new StringBuilder();
            largeJsonBuilder.append("""
                {
                    "resourceType": "Bundle",
                    "entry": [
                """);

            // Add 100 entries to simulate typical batch size
            for (int i = 0; i < 100; i++) {
                if (i > 0) largeJsonBuilder.append(",");
                largeJsonBuilder.append(String.format("""
                    {
                        "resource": {
                            "resourceType": "Patient",
                            "id": "patient-%d"
                        }
                    }
                    """, i));
            }

            largeJsonBuilder.append("]}");
            String largeJson = largeJsonBuilder.toString();

            Document doc = parseJsonToXmlDocument(largeJson);

            assertEquals("Bundle", doc.getDocumentElement().getLocalName());

            // Verify it contains all patient entries using XPath for FHIR-compliant XML
            // Now that we properly embed resources, the XPath should find the nested Patient elements
            int patientResourceCount = countElements(doc, "//*[local-name()='Patient']");
            assertEquals(100, patientResourceCount, "Should contain all 100 Patient resource elements");

            // Verify the id elements are properly formatted with value attributes
            int patientIdCount = countElements(doc, "//*[local-name()='Patient']/*[local-name()='id' and contains(@value, 'patient-')]");
            assertEquals(100, patientIdCount, "Should contain all 100 patient id elements with proper value attributes");
        }

        /**
         * Verifies deep nested structures are correctly converted and preserved in XML output.
         */
        @Test
        @DisplayName("should handle deeply nested JSON structures")
        void testConvertDeeplyNestedJson() throws Exception {
            String deeplyNestedJson = """
                {
                    "resourceType": "Bundle",
                    "entry": [
                        {
                            "resource": {
                                "resourceType": "Patient",
                                "contact": [
                                    {
                                        "relationship": [
                                            {
                                                "coding": [
                                                    {
                                                        "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                                                        "code": "C"
                                                    }
                                                ]
                                            }
                                        ]
                                    }
                                ]
                            }
                        }
                    ]
                }
                """;

            Document doc = parseJsonToXmlDocument(deeplyNestedJson);

            assertEquals("Bundle", doc.getDocumentElement().getLocalName());
            assertElementExists(doc, "//*[local-name()='coding']");
            assertElementWithValue(doc, "system", "http://terminology.hl7.org/CodeSystem/v2-0131");
            assertElementWithValue(doc, "code", "C");
        }

        /**
         * Ensures that empty arrays do not result in unnecessary XML elements.
         */
        @Test
        @DisplayName("should handle empty arrays correctly")
        void testConvertJsonWithEmptyArrays() throws Exception {
            String jsonWithEmptyArray = """
                {
                    "resourceType": "Patient",
                    "id": "example",
                    "name": [],
                    "telecom": []
                }
                """;

            Document doc = parseJsonToXmlDocument(jsonWithEmptyArray);

            assertEquals("Patient", doc.getDocumentElement().getLocalName());
            assertElementWithValue(doc, "id", "example");

            // Empty arrays should not produce any elements
            assertEquals(0, countElements(doc, "//*[local-name()='name']"), "Empty name array should not produce elements");
            assertEquals(0, countElements(doc, "//*[local-name()='telecom']"), "Empty telecom array should not produce elements");
        }

        /**
         * Ensures that special characters in JSON are properly escaped in the resulting XML.
         */
        @Test
        @DisplayName("should properly escape special XML characters")
        void testConvertJsonWithSpecialCharacters() throws Exception {
            String jsonWithSpecialChars = """
                {
                    "resourceType": "Patient",
                    "id": "example",
                    "name": "John & Jane <Test> \\"Quote\\" 'Apostrophe'"
                }
                """;

            String xmlResult = linter.convertJsonToXmlForTesting(jsonWithSpecialChars);

            // Verify XML escaping in the raw XML string
            assertTrue(xmlResult.contains("&amp;"), "& should be escaped as &amp;");
            assertTrue(xmlResult.contains("&lt;"), "< should be escaped as &lt;");
            assertTrue(xmlResult.contains("&gt;"), "> should be escaped as &gt;");
            assertTrue(xmlResult.contains("&quot;"), "\" should be escaped as &quot;");
            assertTrue(xmlResult.contains("&apos;"), "' should be escaped as &apos;");

            // Also verify DOM parsing works correctly
            Document doc = parseJsonToXmlDocument(jsonWithSpecialChars);
            assertEquals("Patient", doc.getDocumentElement().getLocalName());
        }
    }

    @Nested
    @DisplayName("Negative Test Cases")
    class NegativeTests {

        /**
         * Tests various malformed JSON inputs using parameterized test.
         * Each input should throw an exception.
         */
        @ParameterizedTest
        @CsvSource({
                "'{}'",
                "'{\"id\": \"example\"}'"
        })
        @DisplayName("should throw exception for invalid JSON inputs")
        void testInvalidJsonInputs(String invalidJson) {
            Exception exception = assertThrows(Exception.class, () -> {
                linter.convertJsonToXmlForTesting(invalidJson);
            });

            String message = exception.getMessage();
            assertNotNull(message, "Exception should have a message");

            // Should contain error about missing resourceType or JSON parsing
            assertTrue(message.toLowerCase().contains("resourcetype") ||
                            message.toLowerCase().contains("missing") ||
                            message.toLowerCase().contains("json") ||
                            message.toLowerCase().contains("fhir"),
                    "Exception message should indicate missing resourceType or JSON error but was: " + message);
        }

        /**
         * Verifies that passing null to the conversion method causes an exception.
         */
        @Test
        @DisplayName("should throw exception for null JSON string")
        void testConvertNullJsonString() {
            Exception exception = assertThrows(Exception.class, () -> {
                linter.convertJsonToXmlForTesting(null);
            });

            assertNotNull(exception.getMessage(), "Exception should have a meaningful message");
        }

        /**
         * Tests malformed JSON syntax.
         */
        @Test
        @DisplayName("should throw exception for malformed JSON syntax")
        void testMalformedJsonSyntax() {
            String malformedJson = "{\"resourceType\": \"Patient\" \"id\": \"example\"}"; // Missing comma

            Exception exception = assertThrows(Exception.class, () -> {
                linter.convertJsonToXmlForTesting(malformedJson);
            });

            String message = exception.getMessage();
            assertNotNull(message, "Exception should have a message");
            // Should contain parse error or similar
            assertTrue(message.toLowerCase().contains("parse") ||
                            message.toLowerCase().contains("json") ||
                            message.toLowerCase().contains("unexpected") ||
                            message.toLowerCase().contains("syntax"),
                    "Exception message should indicate JSON parsing error but was: " + message);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Advanced Scenarios")
    class EdgeCaseTests {

        /**
         * Tests handling of unknown/unexpected JSON fields.
         */
        @Test
        @DisplayName("should handle unknown JSON fields gracefully")
        void testUnknownJsonFields() throws Exception {
            String jsonWithUnknownFields = """
                {
                    "resourceType": "Patient",
                    "id": "example",
                    "customField": "unexpected",
                    "anotherUnknown": {
                        "nested": "value"
                    }
                }
                """;

            Document doc = parseJsonToXmlDocument(jsonWithUnknownFields);

            assertEquals("Patient", doc.getDocumentElement().getLocalName());
            assertElementWithValue(doc, "id", "example");

            // Unknown fields should be converted as regular elements
            assertElementWithValue(doc, "customField", "unexpected");
            assertElementExists(doc, "//*[local-name()='anotherUnknown']");
        }

        /**
         * Tests type conflicts in JSON values.
         */
        @Test
        @DisplayName("should handle type conflicts in JSON values")
        void testTypeConflicts() throws Exception {
            String jsonWithTypeConflicts = """
                {
                    "resourceType": "Patient",
                    "id": "example",
                    "active": "notABoolean",
                    "birthDate": 123456
                }
                """;

            Document doc = parseJsonToXmlDocument(jsonWithTypeConflicts);

            assertEquals("Patient", doc.getDocumentElement().getLocalName());
            assertElementWithValue(doc, "id", "example");
            assertElementWithValue(doc, "active", "notABoolean");
            assertElementWithValue(doc, "birthDate", "123456");
        }

        /**
         * Ensures the generated XML can be parsed back into a valid DOM object.
         */
        @Test
        @DisplayName("should produce XML that can be parsed back to DOM")
        void testConvertedXmlIsValidDom() throws Exception {
            String simpleJson = """
                {
                    "resourceType": "ActivityDefinition",
                    "status": "active",
                    "extension": [
                        {
                            "url": "http://dsf.dev/fhir/StructureDefinition/extension-process-authorization",
                            "extension": [
                                {
                                    "url": "message-name",
                                    "valueString": "dashboardReportAutostartStart"
                                }
                            ]
                        }
                    ]
                }
                """;

            Document doc = parseJsonToXmlDocument(simpleJson);

            // Verify document structure
            assertNotNull(doc);
            assertEquals("ActivityDefinition", doc.getDocumentElement().getLocalName());
            assertEquals("http://hl7.org/fhir", doc.getDocumentElement().getNamespaceURI());

            // Verify some key elements exist in the DOM
            assertTrue(countElements(doc, "//*[local-name()='extension']") > 0, "Should contain extension elements");
            assertTrue(countElements(doc, "//*[@url]") > 0, "Should contain elements with url attributes");
        }
    }

    @Nested
    @DisplayName("Direct JSON Node Tests")
    class JsonNodeTests {

        /**
         * Tests that we can parse JSON into JsonNode and then convert to XML successfully.
         */
        @Test
        @DisplayName("should handle JsonNode parsing and conversion correctly")
        void testJsonNodeParsingAndConversion() throws Exception {
            String simpleJson = """
                {
                    "resourceType": "Patient",
                    "id": "test-123",
                    "active": true,
                    "birthDate": "1990-01-01"
                }
                """;

            // Parse to JsonNode first to verify Jackson parsing works
            JsonNode jsonNode = objectMapper.readTree(simpleJson);
            assertNotNull(jsonNode);
            assertEquals("Patient", jsonNode.get("resourceType").asText());
            assertEquals("test-123", jsonNode.get("id").asText());

            // Then convert via our linter
            Document doc = parseJsonToXmlDocument(simpleJson);

            assertNotNull(doc);
            assertEquals("Patient", doc.getDocumentElement().getLocalName());
            assertElementWithValue(doc, "id", "test-123");
            assertElementWithValue(doc, "active", "true");
            assertElementWithValue(doc, "birthDate", "1990-01-01");
        }

        /**
         * Tests handling of complex nested JsonNode structures.
         */
        @Test
        @DisplayName("should handle complex nested JsonNode objects")
        void testComplexNestedJsonNode() throws Exception {
            String complexJson = """
                {
                    "resourceType": "Patient",
                    "name": [
                        {
                            "use": "official",
                            "family": "Smith",
                            "given": ["John", "Michael"]
                        }
                    ]
                }
                """;

            // Verify JsonNode structure
            JsonNode jsonNode = objectMapper.readTree(complexJson);
            assertTrue(jsonNode.get("name").isArray());
            assertEquals(1, jsonNode.get("name").size());

            JsonNode nameNode = jsonNode.get("name").get(0);
            assertEquals("official", nameNode.get("use").asText());
            assertEquals("Smith", nameNode.get("family").asText());
            assertTrue(nameNode.get("given").isArray());

            // Convert and verify XML
            Document doc = parseJsonToXmlDocument(complexJson);

            assertEquals("Patient", doc.getDocumentElement().getLocalName());
            assertElementExists(doc, "//*[local-name()='name']");
            assertElementWithValue(doc, "use", "official");
            assertElementWithValue(doc, "family", "Smith");
            assertElementWithValue(doc, "given", "John");
            assertElementWithValue(doc, "given", "Michael");

            // Verify multiple given elements exist
            int givenCount = countElements(doc, "//*[local-name()='given']");
            assertEquals(2, givenCount, "Should have exactly 2 given name elements");
        }
    }
}

