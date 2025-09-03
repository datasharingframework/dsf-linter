package dev.dsf.utils.validator;

import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.util.validation.ValidationOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for JSON support in FHIR resource validation.
 *
 * Note: The current DsfValidatorImpl only supports complete Maven projects
 * with ProcessPluginDefinition. These tests verify that the validator
 * handles JSON files appropriately when they are not part of a complete project.
 */
public class JsonSupportAdditionalTest {

    @TempDir
    Path tempDir;

    private DsfValidatorImpl validator;
    private Path fhirDir;

    /**
     * A "no-op" (no-operation) logger for tests that does nothing.
     */
    private static class NoOpLogger implements Logger {
        @Override
        public void info(String message) { /* Do nothing */ }
        @Override
        public void debug(String message) { /* Do nothing */ }
        @Override
        public void warn(String message) { /* Do nothing */ }
        @Override
        public void error(String message) { /* Do nothing */ }
        @Override
        public void error(String message, Throwable throwable) { /* Do nothing */ }
    }

    @BeforeEach
    void setUp() {
        validator = new DsfValidatorImpl(new NoOpLogger());

        // Create test project structure
        Path srcMain = tempDir.resolve("src").resolve("main").resolve("resources");
        fhirDir = srcMain.resolve("fhir");
        try {
            Files.createDirectories(fhirDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test directory structure", e);
        }
    }

    @Test
    void testJsonSupportForCodeSystem_WithoutCompleteProject() throws IOException, MissingServiceRegistrationException, ResourceValidationException, InterruptedException {
        // Create JSON CodeSystem
        String jsonCodeSystem = """
            {
              "resourceType": "CodeSystem",
              "id": "test-codesystem",
              "url": "http://dsf.dev/fhir/CodeSystem/test",
              "version": "1.0.0",
              "name": "TestCodeSystem",
              "title": "Test Code System",
              "status": "active",
              "publisher": "Test Publisher",
              "content": "complete",
              "caseSensitive": true,
              "concept": [
                {
                  "code": "test-code",
                  "display": "Test Code"
                }
              ]
            }""";

        Files.writeString(fhirDir.resolve("test-codesystem.json"), jsonCodeSystem);

        // The current implementation requires a complete Maven project
        // Without pom.xml and ProcessPluginDefinition, validation should fail or return empty
        assertThrows(RuntimeException.class, () -> validator.validate(tempDir),
                "Should fail validation for incomplete project structure");
    }

    @Test
    void testJsonSupportForValueSet_WithoutCompleteProject() throws IOException, MissingServiceRegistrationException, ResourceValidationException, InterruptedException {
        // Create JSON ValueSet
        String jsonValueSet = """
            {
              "resourceType": "ValueSet",
              "id": "test-valueset",
              "url": "http://dsf.dev/fhir/ValueSet/test",
              "version": "1.0.0",
              "name": "TestValueSet",
              "title": "Test Value Set",
              "status": "active",
              "publisher": "Test Publisher",
              "description": "A test value set",
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

        Files.writeString(fhirDir.resolve("test-valueset.json"), jsonValueSet);

        // The current implementation requires a complete Maven project
        // Without pom.xml and ProcessPluginDefinition, validation should fail or return empty
        assertThrows(RuntimeException.class, () -> validator.validate(tempDir),
                "Should fail validation for incomplete project structure");
    }

    @Test
    void testJsonFileValidation_WithMinimalMavenProject() throws IOException {
        // Create minimal Maven project structure
        createMinimalMavenProject();

        // Create JSON CodeSystem
        String jsonCodeSystem = """
            {
              "resourceType": "CodeSystem",
              "id": "test-codesystem",
              "url": "http://dsf.dev/fhir/CodeSystem/test",
              "version": "1.0.0",
              "name": "TestCodeSystem",
              "title": "Test Code System",
              "status": "active",
              "publisher": "Test Publisher",
              "content": "complete",
              "caseSensitive": true,
              "concept": [
                {
                  "code": "test-code",
                  "display": "Test Code"
                }
              ]
            }""";

        Files.writeString(fhirDir.resolve("test-codesystem.json"), jsonCodeSystem);

        // Even with Maven project, without ProcessPluginDefinition it should fail
        assertThrows(IllegalStateException.class, () -> validator.validate(tempDir),
                "Should fail during ProcessPluginDefinition discovery");
    }

    @Test
    void testSingleJsonFileValidation() throws IOException, MissingServiceRegistrationException, ResourceValidationException, InterruptedException {
        // Create a single JSON file
        String jsonCodeSystem = """
            {
              "resourceType": "CodeSystem",
              "id": "test-codesystem",
              "url": "http://dsf.dev/fhir/CodeSystem/test",
              "version": "1.0.0",
              "name": "TestCodeSystem",
              "title": "Test Code System",
              "status": "active",
              "publisher": "Test Publisher",
              "content": "complete",
              "caseSensitive": true
            }""";

        Path jsonFile = fhirDir.resolve("test-codesystem.json");
        Files.writeString(jsonFile, jsonCodeSystem);

        // Test validation of single file (should return empty output)
        ValidationOutput result = validator.validate(jsonFile);

        assertNotNull(result, "Should return validation output");
        assertTrue(result.validationItems().isEmpty(),
                "Should return empty validation items for single JSON files");
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
}