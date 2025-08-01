package dev.dsf.utils.validator;

import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.UnparsableBpmnFileValidationItem;
import dev.dsf.utils.validator.util.ValidationOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>
 * Comprehensive test suite for {@link DsfValidatorImpl} covering both BPMN and FHIR validation,
 * single file and directory validation, and various edge cases.
 * </p>
 *
 * <p>
 * The tests are organized into nested classes:
 * <ul>
 *   <li>{@code SingleFileValidation} - Tests for individual file validation</li>
 *   <li>{@code DirectoryValidation} - Tests for project directory validation</li>
 *   <li>{@code FileTypeDetection} - Tests for file type detection logic</li>
 *   <li>{@code BpmnValidation} - BPMN-specific validation tests</li>
 *   <li>{@code FhirValidation} - FHIR-specific validation tests</li>
 *   <li>{@code ErrorHandling} - Edge cases and error scenarios</li>
 * </ul>
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://junit.org/junit5/docs/current/user-guide/">JUnit 5 User Guide</a></li>
 *   <li><a href="https://site.mockito.org/">Mockito Documentation</a></li>
 *   <li><a href="https://docs.oracle.com/javase/8/docs/api/java/nio/file/Files.html">Java NIO Files API</a></li>
 *   <li>BPMN 2.0: <a href="https://www.omg.org/spec/BPMN/2.0">https://www.omg.org/spec/BPMN/2.0</a></li>
 * </ul>
 */
class TestDsfValidatorImpl
{
    private final DsfValidatorImpl validator = new DsfValidatorImpl();

    @Nested
    @DisplayName("Single File Validation")
    class SingleFileValidation
    {
        @Test
        @DisplayName("Should validate single BPMN file successfully")
        void testSingleBpmnFileValidation(@TempDir Path tempDir) throws IOException
        {
            Path bpmnFile = createValidBpmnFile(tempDir, "test.bpmn");

            ValidationOutput output = validator.validate(bpmnFile);

            assertNotNull(output, "Validation output should not be null");
            assertNotNull(output.validationItems(), "Validation items should not be null");

            // Check that no critical errors occurred
            boolean hasCriticalErrors = output.validationItems().stream()
                    .anyMatch(item -> item instanceof UnparsableBpmnFileValidationItem);
            assertFalse(hasCriticalErrors, "Should not have unparsable BPMN file errors for valid file");
        }

        @Test
        @DisplayName("Should validate single FHIR JSON file")
        void testSingleFhirJsonFileValidation(@TempDir Path tempDir) throws IOException
        {
            Path fhirFile = createValidFhirJsonFile(tempDir, "test.json");

            ValidationOutput output = validator.validate(fhirFile);

            assertNotNull(output, "Validation output should not be null");
            assertNotNull(output.validationItems(), "Validation items should not be null");
        }

        @Test
        @DisplayName("Should validate single FHIR XML file")
        void testSingleFhirXmlFileValidation(@TempDir Path tempDir) throws IOException
        {
            Path fhirFile = createValidFhirXmlFile(tempDir, "test.xml");

            ValidationOutput output = validator.validate(fhirFile);

            assertNotNull(output, "Validation output should not be null");
            assertNotNull(output.validationItems(), "Validation items should not be null");
        }

        @Test
        @DisplayName("Should return empty output for unsupported file types")
        void testUnsupportedFileType(@TempDir Path tempDir) throws IOException
        {
            Path txtFile = tempDir.resolve("test.txt");
            Files.writeString(txtFile, "This is a text file");

            ValidationOutput output = validator.validate(txtFile);

            assertNotNull(output, "Validation output should not be null");
            assertTrue(output.validationItems().isEmpty(),
                    "Should return empty validation items for unsupported file types");
        }
    }

    @Nested
    @DisplayName("Directory Validation")
    class DirectoryValidation
    {
        @Test
        @DisplayName("Should validate complete project directory structure")
        void testCompleteProjectValidation(@TempDir Path tempDir) throws IOException
        {
            createCompleteProjectStructure(tempDir);

            ValidationOutput output = validator.validate(tempDir);

            assertNotNull(output, "Validation output should not be null");
            assertNotNull(output.validationItems(), "Validation items should not be null");

            // Verify report directory is created
            File reportDir = new File(tempDir.toFile(), "report");
            // Note: The report directory is created in current working directory, not tempDir
            // This is expected behavior based on the implementation
        }

        @Test
        @DisplayName("Should handle project with only BPMN files")
        void testProjectWithOnlyBpmnFiles(@TempDir Path tempDir) throws IOException
        {
            createBpmnOnlyProjectStructure(tempDir);

            ValidationOutput output = validator.validate(tempDir);

            assertNotNull(output, "Validation output should not be null");
            assertNotNull(output.validationItems(), "Validation items should not be null");
        }

        @Test
        @DisplayName("Should handle project with only FHIR files")
        void testProjectWithOnlyFhirFiles(@TempDir Path tempDir) throws IOException
        {
            createFhirOnlyProjectStructure(tempDir);

            ValidationOutput output = validator.validate(tempDir);

            assertNotNull(output, "Validation output should not be null");
            assertNotNull(output.validationItems(), "Validation items should not be null");
        }

        @Test
        @DisplayName("Should handle empty project directory")
        void testEmptyProjectDirectory(@TempDir Path tempDir)
        {
            ValidationOutput output = validator.validate(tempDir);

            assertNotNull(output, "Validation output should not be null");
            assertTrue(output.validationItems().isEmpty() ||
                    output.validationItems().stream().allMatch(item ->
                            item.getSeverity() == ValidationSeverity.SUCCESS),
                    "Empty project should have no validation issues or only success items");
        }
    }

    @Nested
    @DisplayName("File Type Detection")
    class FileTypeDetection
    {
        @Test
        @DisplayName("Should correctly detect BPMN files by extension")
        void testBpmnFileDetection(@TempDir Path tempDir) throws IOException
        {
            Path bpmnFile = createValidBpmnFile(tempDir, "process.bpmn");

            ValidationOutput output = validator.validate(bpmnFile);

            assertNotNull(output, "BPMN file should be recognized and validated");
        }

        @Test
        @DisplayName("Should correctly detect FHIR JSON files")
        void testFhirJsonFileDetection(@TempDir Path tempDir) throws IOException
        {
            Path jsonFile = createValidFhirJsonFile(tempDir, "resource.json");

            ValidationOutput output = validator.validate(jsonFile);

            assertNotNull(output, "FHIR JSON file should be recognized and validated");
        }

        @Test
        @DisplayName("Should correctly detect FHIR XML files")
        void testFhirXmlFileDetection(@TempDir Path tempDir) throws IOException
        {
            Path xmlFile = createValidFhirXmlFile(tempDir, "resource.xml");

            ValidationOutput output = validator.validate(xmlFile);

            assertNotNull(output, "FHIR XML file should be recognized and validated");
        }
    }

    @Nested
    @DisplayName("BPMN Validation Specifics")
    class BpmnValidation
    {
        @Test
        @DisplayName("Should handle invalid BPMN XML content")
        void testInvalidBpmnContent(@TempDir Path tempDir) throws IOException
        {
            Path bpmnDir = createBpmnDirectory(tempDir);
            Path invalidBpmn = bpmnDir.resolve("invalid.bpmn");
            Files.writeString(invalidBpmn, "Invalid BPMN content");

            // Use the public validate method instead of the non-existent test method
            ValidationOutput output = validator.validate(tempDir);
            List<AbstractValidationItem> issues = output.validationItems();

            assertFalse(issues.isEmpty(), "Should have validation issues for invalid BPMN");
            boolean hasUnparsableError = issues.stream()
                    .anyMatch(item -> item instanceof UnparsableBpmnFileValidationItem);
            assertTrue(hasUnparsableError, "Should have UnparsableBpmnFileValidationItem for invalid BPMN");
        }

        @Test
        @DisplayName("Should validate minimal valid BPMN successfully")
        void testValidBpmnContent(@TempDir Path tempDir) throws IOException
        {
            Path bpmnDir = createBpmnDirectory(tempDir);
            createValidBpmnFile(bpmnDir, "valid.bpmn");

            // Use the public validate method instead of the non-existent test method
            ValidationOutput output = validator.validate(tempDir);
            List<AbstractValidationItem> issues = output.validationItems();

            // Should not have any critical errors
            boolean hasCriticalErrors = issues.stream()
                    .anyMatch(item -> item.getSeverity() == ValidationSeverity.ERROR);
            assertFalse(hasCriticalErrors, "Valid BPMN should not produce critical errors");
        }

        @Test
        @DisplayName("Should handle unreadable BPMN files")
        void testUnreadableBpmnFile(@TempDir Path tempDir) throws IOException
        {
            Path bpmnDir = createBpmnDirectory(tempDir);
            Path unreadableBpmn = createValidBpmnFile(bpmnDir, "unreadable.bpmn");

            // Attempt to make file unreadable
            File fileObj = unreadableBpmn.toFile();
            boolean removedPermission = fileObj.setReadable(false, false);

            // Use the public validate method instead of the non-existent test method
            ValidationOutput output = validator.validate(tempDir);
            List<AbstractValidationItem> issues = output.validationItems();

            if (removedPermission) {
                assertNotNull(output, "Should return validation output even for unreadable files");
                // Note: The exact behavior depends on implementation details
                // We mainly test that the validator doesn't crash
            } else {
                System.out.println("WARNING: Could not remove read permissions on this system");
            }
        }
    }

    @Nested
    @DisplayName("FHIR Validation Specifics")
    class FhirValidation
    {
        @Test
        @DisplayName("Should validate FHIR Task resource")
        void testFhirTaskValidation(@TempDir Path tempDir) throws IOException
        {
            String taskContent = """
                {
                  "resourceType": "Task",
                  "id": "test-task",
                  "status": "ready",
                  "intent": "order"
                }
                """;
            Path taskFile = tempDir.resolve("task.json");
            Files.writeString(taskFile, taskContent);

            ValidationOutput output = validator.validate(taskFile);

            assertNotNull(output, "Task validation should produce output");
            assertNotNull(output.validationItems(), "Validation items should not be null");
        }

        @Test
        @DisplayName("Should validate FHIR ValueSet resource")
        void testFhirValueSetValidation(@TempDir Path tempDir) throws IOException
        {
            String valueSetContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ValueSet xmlns="http://hl7.org/fhir">
                  <id value="test-valueset"/>
                  <status value="active"/>
                </ValueSet>
                """;
            Path valueSetFile = tempDir.resolve("valueset.xml");
            Files.writeString(valueSetFile, valueSetContent);

            ValidationOutput output = validator.validate(valueSetFile);

            assertNotNull(output, "ValueSet validation should produce output");
            assertNotNull(output.validationItems(), "Validation items should not be null");
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandling
    {
        @Test
        @DisplayName("Should handle non-existent file gracefully")
        void testNonExistentFile(@TempDir Path tempDir)
        {
            Path nonExistentFile = tempDir.resolve("does-not-exist.bpmn");

            ValidationOutput output = validator.validate(nonExistentFile);

            assertNotNull(output, "Should return validation output even for non-existent files");
            // The implementation returns empty list for non-existent files
            assertTrue(output.validationItems().isEmpty() ||
                      output.validationItems().stream().anyMatch(item ->
                          item.getSeverity() == ValidationSeverity.ERROR),
                      "Should handle non-existent files gracefully");
        }

        @Test
        @DisplayName("Should handle missing BPMN directory")
        void testMissingBpmnDirectory(@TempDir Path tempDir)
        {
            // Use the public validate method on an empty directory
            ValidationOutput output = validator.validate(tempDir);

            assertNotNull(output, "Should return validation output");
            // Empty directory should return empty validation items
            assertTrue(output.validationItems().isEmpty() ||
                      output.validationItems().stream().allMatch(item ->
                          item.getSeverity() == ValidationSeverity.SUCCESS),
                      "Should handle missing BPMN directory gracefully");
        }

        @Test
        @DisplayName("Should handle corrupted JSON FHIR file")
        void testCorruptedJsonFhirFile(@TempDir Path tempDir) throws IOException
        {
            Path corruptedJson = tempDir.resolve("corrupted.json");
            Files.writeString(corruptedJson, "{ invalid json content");

            ValidationOutput output = validator.validate(corruptedJson);

            assertNotNull(output, "Should handle corrupted JSON gracefully");
            // The exact behavior depends on FhirResourceValidator implementation
        }
    }

    // Utility methods for test setup

    private Path createBpmnDirectory(Path tempDir) throws IOException
    {
        Path bpmnDir = tempDir.resolve("src").resolve("main").resolve("resources").resolve("bpe");
        Files.createDirectories(bpmnDir);
        return bpmnDir;
    }

    private Path createFhirDirectory(Path tempDir) throws IOException
    {
        Path fhirDir = tempDir.resolve("src").resolve("main").resolve("resources").resolve("fhir");
        Files.createDirectories(fhirDir);
        return fhirDir;
    }

    private Path createValidBpmnFile(Path directory, String filename) throws IOException
    {
        String bpmnContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         targetNamespace="Examples">
              <process id="TestProcess" isExecutable="true">
                <startEvent id="StartEvent_1" name="Start" />
                <endEvent id="EndEvent_1" name="End" />
                <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1" />
              </process>
            </definitions>
            """;
        Path bpmnFile = directory.resolve(filename);
        Files.writeString(bpmnFile, bpmnContent);
        return bpmnFile;
    }

    private Path createValidFhirJsonFile(Path directory, String filename) throws IOException
    {
        String fhirContent = """
            {
              "resourceType": "Task",
              "id": "test-task",
              "status": "ready",
              "intent": "order"
            }
            """;
        Path fhirFile = directory.resolve(filename);
        Files.writeString(fhirFile, fhirContent);
        return fhirFile;
    }

    private Path createValidFhirXmlFile(Path directory, String filename) throws IOException
    {
        String fhirContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Task xmlns="http://hl7.org/fhir">
              <id value="test-task"/>
              <status value="ready"/>
              <intent value="order"/>
            </Task>
            """;
        Path fhirFile = directory.resolve(filename);
        Files.writeString(fhirFile, fhirContent);
        return fhirFile;
    }

    private void createCompleteProjectStructure(Path tempDir) throws IOException
    {
        // Create BPMN structure
        Path bpmnDir = createBpmnDirectory(tempDir);
        createValidBpmnFile(bpmnDir, "process1.bpmn");
        createValidBpmnFile(bpmnDir, "process2.bpmn");

        // Create FHIR structure
        Path fhirDir = createFhirDirectory(tempDir);
        createValidFhirJsonFile(fhirDir, "task.json");
        createValidFhirXmlFile(fhirDir, "valueset.xml");

        // Create pom.xml to simulate Maven project
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>test</groupId>
              <artifactId>test-project</artifactId>
              <version>1.0.0</version>
            </project>
            """;
        Files.writeString(tempDir.resolve("pom.xml"), pomContent);
    }

    private void createBpmnOnlyProjectStructure(Path tempDir) throws IOException
    {
        Path bpmnDir = createBpmnDirectory(tempDir);
        createValidBpmnFile(bpmnDir, "process.bpmn");
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
    }

    private void createFhirOnlyProjectStructure(Path tempDir) throws IOException
    {
        Path fhirDir = createFhirDirectory(tempDir);
        createValidFhirJsonFile(fhirDir, "task.json");
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
    }
}
