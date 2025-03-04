package dev.dsf.utils.validator.fhir;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link FhirValidator} class.
 * <p>
 * This test class verifies the functionality of the FHIR resource validator,
 * which checks for the presence and content of local FHIR ActivityDefinition and
 * StructureDefinition XML resources. The tests ensure that:
 * <ul>
 *   <li>An ActivityDefinition referencing a given message is detected.</li>
 *   <li>A StructureDefinition containing the expected URL is found.</li>
 *   <li>Message-name values are correctly extracted from StructureDefinition files.</li>
 *   <li>The XML parsing utility method can parse an XML file via reflection.</li>
 * </ul>
 * </p>
 * <p>
 * References:
 * <ul>
 *   <li><a href="https://www.hl7.org/fhir/">FHIR Specification</a></li>
 *   <li><a href="https://docs.oracle.com/en/java/javase/17/docs/specs/javadoc/doc-comment-spec.html">
 *       Oracle JavaDoc Guidelines</a></li>
 * </ul>
 * </p>
 */
class TestFhirValidator {

    /**
     * Tests that an ActivityDefinition XML file in a mocked directory structure is detected.
     * <p>
     * This test creates a temporary directory structure representing the expected location of
     * FHIR ActivityDefinition files. A minimal XML file is written which includes an extension
     * with the value "MyMessage". The test then asserts that the {@link FhirValidator#activityDefinitionExists(String, java.io.File)}
     * method returns true when searching for this message.
     * </p>
     *
     * @param tempDir a temporary directory provided by JUnit
     * @throws IOException if an I/O error occurs during file operations
     */
    @Test
    @DisplayName("Should detect an ActivityDefinition in a mocked directory structure")
    void testActivityDefinitionExists(@TempDir Path tempDir) throws IOException {
        // Mock up the ACTIVITY_DEFINITION_DIR subdirectory
        Path activityDefDir = tempDir.resolve("src").resolve("main").resolve("resources")
                .resolve("fhir").resolve("ActivityDefinition");
        Files.createDirectories(activityDefDir);

        // Create a minimal ActivityDefinition file containing 'MyMessage' as an extension
        Path actDefFile = activityDefDir.resolve("my-activitydef.xml");
        String xmlContent = """
            <ActivityDefinition xmlns="http://hl7.org/fhir">
              <extension url="message-name">
                <valueString value="MyMessage"/>
              </extension>
            </ActivityDefinition>
            """;
        Files.writeString(actDefFile, xmlContent);

        // Now call FhirValidator.activityDefinitionExists(...)
        boolean result = FhirValidator.activityDefinitionExists("MyMessage", tempDir.toFile());
        assertTrue(result, "Should find an ActivityDefinition referencing 'MyMessage'");
    }

    /**
     * Tests that a StructureDefinition XML file in a mocked directory structure is detected.
     * <p>
     * This test creates a temporary directory structure representing the expected location of
     * FHIR StructureDefinition files. A minimal XML file is written containing a URL value.
     * The test asserts that the {@link FhirValidator#structureDefinitionExists(String, java.io.File)}
     * method returns true when the URL is present.
     * </p>
     *
     * @param tempDir a temporary directory provided by JUnit
     * @throws IOException if an I/O error occurs during file operations
     */
    @Test
    @DisplayName("Should detect a StructureDefinition in a mocked directory structure")
    void testStructureDefinitionExists(@TempDir Path tempDir) throws IOException {
        // Mock up the STRUCTURE_DEFINITION_DIR subdirectory
        Path structDefDir = tempDir.resolve("src").resolve("main").resolve("resources")
                .resolve("fhir").resolve("StructureDefinition");
        Files.createDirectories(structDefDir);

        // Create a minimal StructureDefinition file containing 'http://example.org/myProfile'
        Path structDefFile = structDefDir.resolve("my-structdef.xml");
        String xmlContent = """
            <StructureDefinition xmlns="http://hl7.org/fhir">
              <url value="http://example.org/myProfile"/>
            </StructureDefinition>
            """;
        Files.writeString(structDefFile, xmlContent);

        boolean result = FhirValidator.structureDefinitionExists("http://example.org/myProfile", tempDir.toFile());
        assertTrue(result, "Should find a StructureDefinition with the correct URL");
    }

    /**
     * Tests that message-name values are correctly extracted from StructureDefinition XML files.
     * <p>
     * This test creates two minimal StructureDefinition XML files in a temporary directory, each containing
     * a fixedString value representing a message-name ("ping" and "pong"). The test asserts that the method
     * {@link FhirValidator#getAllMessageValuesFromStructureDefinitions(java.io.File)} returns a set containing
     * both values and that exactly two distinct message-name values are found.
     * </p>
     *
     * @param tempDir a temporary directory provided by JUnit
     * @throws IOException if an I/O error occurs during file operations
     */
    @Test
    @DisplayName("Should parse StructureDefinition and find message-name values")
    void testGetAllMessageValuesFromStructureDefinitions(@TempDir Path tempDir) throws IOException {
        // Create directories for StructureDefinition files
        Path structDefDir = tempDir.resolve("src").resolve("main").resolve("resources")
                .resolve("fhir").resolve("StructureDefinition");
        Files.createDirectories(structDefDir);

        // Create first minimal XML containing fixedString "ping"
        Path file1 = structDefDir.resolve("sd1.xml");
        String xmlContent1 = """
            <StructureDefinition xmlns="http://hl7.org/fhir">
              <element id="Task.input:message-name.value[x]">
                <fixedString value="ping"/>
              </element>
            </StructureDefinition>
            """;
        Files.writeString(file1, xmlContent1);

        // Create second minimal XML containing fixedString "pong"
        Path file2 = structDefDir.resolve("sd2.xml");
        String xmlContent2 = """
            <StructureDefinition xmlns="http://hl7.org/fhir">
              <element id="Task.input:message-name.value[x]">
                <fixedString value="pong"/>
              </element>
            </StructureDefinition>
            """;
        Files.writeString(file2, xmlContent2);

        Set<String> results = FhirValidator.getAllMessageValuesFromStructureDefinitions(tempDir.toFile());
        assertTrue(results.contains("ping"), "Should contain 'ping'");
        assertTrue(results.contains("pong"), "Should contain 'pong'");
        assertEquals(2, results.size(), "Should contain exactly 2 distinct message-name values");
    }

    /**
     * Tests that an XML file can be parsed using the private {@code parseXml} method via reflection.
     * <p>
     * This test writes a minimal XML file to a temporary directory, then uses reflection to invoke the
     * private static method . It asserts that the returned {@link Document}
     * object is not null, indicating that the XML was successfully parsed.
     * </p>
     *
     * @param tempDir a temporary directory provided by JUnit
     * @throws Exception if reflection fails or an error occurs during XML parsing
     */
    @Test
    @DisplayName("Should parse an XML file with parseXml(...) via reflection (setAccessible)")
    void testParseXmlViaReflection(@TempDir Path tempDir) throws Exception {
        // Create a minimal XML file
        Path xmlFile = tempDir.resolve("myFile.xml");
        String xmlContent = """
        <root>
            <child>Value</child>
        </root>
        """;
        Files.writeString(xmlFile, xmlContent);

        // Reflective access to the private parseXml method in FhirValidator
        var parseMethod = FhirValidator.class.getDeclaredMethod("parseXml", Path.class);
        parseMethod.setAccessible(true);

        Document doc = (Document) parseMethod.invoke(null, xmlFile);
        assertNotNull(doc, "Document should be parsed successfully");
    }
}
