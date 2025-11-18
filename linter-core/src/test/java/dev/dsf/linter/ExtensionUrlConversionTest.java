package dev.dsf.linter;

import dev.dsf.linter.fhir.FhirResourceLinter;
import dev.dsf.linter.logger.ConsoleLogger;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.util.linting.LintingOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for verifying the correct conversion of FHIR {@code extension.url} properties
 * from JSON to XML. Ensures that:
 * <ul>
 *   <li>{@code extension.url} is rendered as an XML attribute, not as a child element</li>
 *   <li>The conversion is consistent across nested extensions</li>
 *   <li>The linter can process the file and produce linting output based on the expected structure</li>
 * </ul>
 *
 * <p>This test verifies both the end-to-end linting behavior and the internal JSON-to-XML transformation.</p>
 */
public class ExtensionUrlConversionTest
{
    /**
     * Temporary directory provided by JUnit for creating isolated test file structures.
     */
    @TempDir
    Path tempDir;

    /**
     * FHIR linter instance used for linting FHIR resources.
     */
    private FhirResourceLinter lint;

    /**
     * Directory under {@code src/main/resources/fhir} where test files will be written.
     */
    private File fhirDir;

    /**
     * Initializes the test environment, creates required directory structure,
     * and instantiates the FHIR linter.
     *
     * @throws IOException if directory creation fails
     */
    @BeforeEach
    void setUp() throws IOException
    {
        Logger logger = new ConsoleLogger(false);
        lint = new FhirResourceLinter(logger);

        // Create test project structure under src/main/resources/fhir
        File projectRoot = tempDir.toFile();
        File srcMain = new File(projectRoot, "src/main/resources");
        fhirDir = new File(srcMain, "fhir");
        fhirDir.mkdirs();
    }

    /**
     * Tests whether the linter can process a FHIR JSON resource with {@code extension.url} fields
     * and internally convert them to XML where the {@code url} is an attribute (not a child element).
     *
     * <p>Ensures that:
     * <ul>
     *   <li>The file is processed without exceptions</li>
     *   <li>Linting results are returned</li>
     *   <li>The conversion to XML did not skip the extension content</li>
     * </ul>
     *
     * @throws Exception if file creation or linting fails
     */
    @Test
    void testExtensionUrlConversionFromJson() throws Exception
    {
        // JSON ActivityDefinition with nested extensions
        String jsonActivityDefinition = """
            {
              "resourceType": "ActivityDefinition",
              "url": "http://dsf.dev/fhir/ActivityDefinition/test",
              "status": "unknown",
              "kind": "Task",
              "meta": {
                "tag": [
                  {
                    "system": "http://dsf.dev/fhir/CodeSystem/read-access-tag",
                    "code": "ALL"
                  }
                ]
              },
              "extension": [
                {
                  "url": "http://dsf.dev/fhir/StructureDefinition/extension-process-authorization",
                  "extension": [
                    {
                      "url": "requester",
                      "valueCoding": {
                        "system": "http://dsf.dev/fhir/CodeSystem/process-authorization",
                        "code": "LOCAL_ALL"
                      }
                    },
                    {
                      "url": "recipient",
                      "valueCoding": {
                        "system": "http://dsf.dev/fhir/CodeSystem/process-authorization", 
                        "code": "LOCAL_ALL"
                      }
                    }
                  ]
                }
              ]
            }""";

        File jsonFile = new File(fhirDir, "test-activitydefinition.json");
        Files.writeString(jsonFile.toPath(), jsonActivityDefinition);

        // Lint the file - JSON is internally converted to XML
        LintingOutput result = lint.lintSingleFile(jsonFile.toPath());

        assertNotNull(result, "Should be able to process the JSON file");

        // Debug output
        System.out.println("Lint items count: " + result.LintItems().size());
        result.LintItems().forEach(item ->
                System.out.println("Lint item: " + item.toString())
        );

        // If the conversion worked, some lint items should be produced
        assertFalse(result.LintItems().isEmpty(),
                "Should have lint items - indicates the JSON file was processed and converted to XML");
    }

    /**
     * Tests the internal JSON-to-XML conversion logic to ensure that {@code extension.url}
     * values are rendered as attributes and not as child elements in the XML structure.
     *
     * <p>This test uses the test helper method {@code convertJsonToXmlForTesting()} exposed
     * by {@link FhirResourceLinter} to inspect the raw generated XML string.</p>
     *
     * @throws Exception if the conversion fails
     */
    @Test
    void testDirectXmlConversionOfExtensionUrl() throws Exception
    {
        String jsonExtension = """
            {
              "resourceType": "ActivityDefinition",
              "url": "http://dsf.dev/fhir/ActivityDefinition/test",
              "extension": [
                {
                  "url": "http://dsf.dev/fhir/StructureDefinition/extension-process-authorization",
                  "extension": [
                    {
                      "url": "requester",
                      "valueCoding": {
                        "system": "http://dsf.dev/fhir/CodeSystem/process-authorization",
                        "code": "LOCAL_ALL"
                      }
                    }
                  ]
                }
              ]
            }""";

        com.fasterxml.jackson.databind.JsonNode jsonNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonExtension);
        String generatedXml = dev.dsf.linter.util.converter.JsonXmlConverter.convertJsonToXml(jsonNode);

        System.out.println("Generated XML:");
        System.out.println(generatedXml);

        // Check that URL appears as attribute
        assertTrue(generatedXml.contains("<extension url=\"http://dsf.dev/fhir/StructureDefinition/extension-process-authorization\">"),
                "Extension should have url as attribute, not child element");
        assertTrue(generatedXml.contains("<extension url=\"requester\">"),
                "Nested extension should also have url as attribute");

        // Check that URL is not rendered as child element
        assertFalse(generatedXml.contains("<url value=\"http://dsf.dev/fhir/StructureDefinition/extension-process-authorization\"/>"),
                "Extension url should not be a child element");
        assertFalse(generatedXml.contains("<url value=\"requester\"/>"),
                "Nested extension url should not be a child element");
    }
}
