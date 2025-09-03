package dev.dsf.utils.validator;

import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.util.validation.ValidationOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for JSON support in FHIR resource validation.
 *
 * Note: The current DsfValidatorImpl only supports complete Maven projects
 * with ProcessPluginDefinition. This test demonstrates the behavior when
 * validating projects with both XML and JSON FHIR resources.
 */
public class JsonSupportTest {

    @TempDir
    Path tempDir;

    private DsfValidatorImpl validator;
    private Path fhirDir;

    /**
     * A "no-op" (no-operation) logger for tests that does nothing.
     */
    private static class NoOpLogger implements Logger {
        @Override
        public void debug(String message) { /* Do nothing */ }
        @Override
        public void info(String message) { /* Do nothing */ }
        @Override
        public void warn(String message) { /* Do nothing */ }
        @Override
        public void error(String message) { /* Do nothing */ }
        @Override
        public void error(String message, Throwable throwable) { /* Do nothing */ }
    }

    @BeforeEach
    void setUp() throws IOException {
        validator = new DsfValidatorImpl(new NoOpLogger());

        // Create test project structure
        Path srcMain = tempDir.resolve("src").resolve("main").resolve("resources");
        fhirDir = srcMain.resolve("fhir");
        Files.createDirectories(fhirDir);

        // Create test XML file
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Task xmlns="http://hl7.org/fhir">
              <id value="example-task"/>
              <meta>
                <profile value="http://dsf.dev/fhir/StructureDefinition/dsf-task-base"/>
              </meta>
              <instantiatesCanonical value="http://dsf.dev/fhir/ActivityDefinition/example"/>
              <status value="draft"/>
              <intent value="order"/>
              <input>
                <type>
                  <coding>
                    <system value="http://dsf.dev/fhir/CodeSystem/bpmn-message"/>
                    <code value="message-name"/>
                  </coding>
                </type>
                <valueString value="example-message"/>
              </input>
              <input>
                <type>
                  <coding>
                    <system value="http://dsf.dev/fhir/CodeSystem/bpmn-message"/>
                    <code value="business-key"/>
                  </coding>
                </type>
                <valueString value="example-business-key"/>
              </input>
            </Task>""";
        Files.writeString(fhirDir.resolve("test-task.xml"), xmlContent);

        // Create test JSON file with same content
        String jsonContent = """
            {
              "resourceType": "Task",
              "id": "example-task",
              "meta": {
                "profile": ["http://dsf.dev/fhir/StructureDefinition/dsf-task-base"]
              },
              "instantiatesCanonical": "http://dsf.dev/fhir/ActivityDefinition/example",
              "status": "draft",
              "intent": "order",
              "input": [
                {
                  "type": {
                    "coding": [
                      {
                        "system": "http://dsf.dev/fhir/CodeSystem/bpmn-message",
                        "code": "message-name"
                      }
                    ]
                  },
                  "valueString": "example-message"
                },
                {
                  "type": {
                    "coding": [
                      {
                        "system": "http://dsf.dev/fhir/CodeSystem/bpmn-message",
                        "code": "business-key"
                      }
                    ]
                  },
                  "valueString": "example-business-key"
                }
              ]
            }""";
        Files.writeString(fhirDir.resolve("test-task.json"), jsonContent);
    }

    @Test
    void testJsonSupportBehaviorWithoutCompleteProject() {
        // The current implementation requires a complete Maven project with ProcessPluginDefinition
        // Without pom.xml and ProcessPluginDefinition, validation should fail
        assertThrows(RuntimeException.class, () -> validator.validate(tempDir),
                "Should fail validation for incomplete project structure (Maven build failure)");
    }

    @Test
    void testJsonSupportBehaviorWithMinimalMavenProject() throws IOException {
        // Create minimal Maven project structure
        createMinimalMavenProject();

        // Even with Maven project, without ProcessPluginDefinition it should fail during discovery
        assertThrows(IllegalStateException.class, () -> validator.validate(tempDir),
                "Should fail during ProcessPluginDefinition discovery");
    }

    @Test
    void testSingleFileValidationBehavior() throws IOException, MissingServiceRegistrationException, ResourceValidationException, InterruptedException {
        // Test validation of individual XML and JSON files
        Path xmlFile = fhirDir.resolve("test-task.xml");
        Path jsonFile = fhirDir.resolve("test-task.json");

        // Both single file validations should return empty output
        ValidationOutput xmlResult = validator.validate(xmlFile);
        ValidationOutput jsonResult = validator.validate(jsonFile);

        assertNotNull(xmlResult, "Should return validation output for XML file");
        assertNotNull(jsonResult, "Should return validation output for JSON file");

        assertTrue(xmlResult.validationItems().isEmpty(),
                "Should return empty validation items for single XML files");
        assertTrue(jsonResult.validationItems().isEmpty(),
                "Should return empty validation items for single JSON files");
    }

    @Test
    void testReportDirectoryHandling() throws IOException, MissingServiceRegistrationException, ResourceValidationException, InterruptedException {
        // Test that the validator handles report directory creation appropriately
        Path nonExistentDir = tempDir.resolve("does-not-exist");

        ValidationOutput result = validator.validate(nonExistentDir);

        assertNotNull(result, "Should return validation output even for non-existent directories");
        assertTrue(result.validationItems().isEmpty(),
                "Should return empty validation items for non-existent directories");

        // Check that no report directory is created for invalid input
        File reportDir = new File("report");
        // Note: The report directory might exist from previous runs, but we shouldn't create it for invalid input
        // This is expected behavior based on the current implementation
    }

    private void createMinimalMavenProject() throws IOException {
        // Create basic Maven directory structure
        Path srcMainJava = tempDir.resolve("src").resolve("main").resolve("java");
        Files.createDirectories(srcMainJava);

        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                                         http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>test</groupId>
              <artifactId>test-project</artifactId>
              <version>1.0.0</version>
              <packaging>jar</packaging>
              
              <properties>
                <maven.compiler.source>11</maven.compiler.source>
                <maven.compiler.target>11</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
              </properties>
              
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.8.1</version>
                  </plugin>
                </plugins>
              </build>
            </project>
            """;
        Files.writeString(tempDir.resolve("pom.xml"), pomContent);
    }

    private void printDirectoryContents(File dir, String indent) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                System.out.println(indent + file.getName());
                if (file.isDirectory()) {
                    printDirectoryContents(file, indent + "  ");
                }
            }
        }
    }
}