package dev.dsf.linter;

import dev.dsf.linter.logger.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test class for JSON support in FHIR resource linting.
 */
public class JsonSupportAdditionalTest {

    @TempDir
    Path tempDir;

    private Path fhirDir;

    /**
     * A "no-op" logger for tests that does nothing.
     */
    private static class NoOpLogger implements Logger {
        @Override
        public void info(String message) { /* Do nothing */ }
        @Override
        public void debug(String message) { /* Do nothing */ }

        @Override
        public boolean verbose() {
            return false;
        }

        @Override
        public boolean isVerbose() {
            return false;
        }

        @Override
        public void warn(String message) { /* Do nothing */ }
        @Override
        public void error(String message) { /* Do nothing */ }
        @Override
        public void error(String message, Throwable throwable) { /* Do nothing */ }
    }

    @BeforeEach
    void setUp() {
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
    @DisplayName("Should fail linting for CodeSystem without a complete project structure")
    void testJsonSupportForCodeSystem_WithoutCompleteProject() throws IOException {
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
        DsfLinter.Config config = new DsfLinter.Config(
                tempDir,
                tempDir.resolve("report"),
                false,
                true,
                false,
                null,
                null,
                new NoOpLogger()
        );
        DsfLinter linter = new DsfLinter(config);

        assertThrows(IOException.class, linter::lint,
                "Should fail linting for incomplete project structure");
    }

    @Test
    @DisplayName("Should fail linting for ValueSet without a complete project structure")
    void testJsonSupportForValueSet_WithoutCompleteProject() throws IOException {
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
        DsfLinter.Config config = new DsfLinter.Config(
                tempDir,
                tempDir.resolve("report"),
                false,
                true,
                false,
                null,
                null,
                new NoOpLogger()
        );
        DsfLinter linter = new DsfLinter(config);

        assertThrows(IOException.class, linter::lint,
                "Should fail linting for incomplete project structure");
    }

    @Test
    @DisplayName("Should fail linting for JSON files even with a minimal Maven project if no ProcessPluginDefinition exists")
    void testJsonFileLinting_WithMinimalMavenProject() throws IOException {
        createMinimalMavenProject();
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
        DsfLinter.Config config = new DsfLinter.Config(
                tempDir,
                tempDir.resolve("report"),
                false,
                true,
                false,
                null,
                null,
                new NoOpLogger()
        );
        DsfLinter linter = new DsfLinter(config);

        assertThrows(IOException.class, linter::lint,
                "Should fail during ProcessPluginDefinition discovery");
    }

    private void createMinimalMavenProject() throws IOException {
        Path srcMainJava = tempDir.resolve("src").resolve("main").resolve("java");
        Files.createDirectories(srcMainJava);
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
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