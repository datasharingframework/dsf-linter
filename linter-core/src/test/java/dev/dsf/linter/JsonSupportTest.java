package dev.dsf.linter;

import dev.dsf.linter.logger.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test class for JSON support in FHIR resource linting.
 * Note: The current DsfLinter only supports complete Maven projects
 * with ProcessPluginDefinition. This test demonstrates the behavior when
 * linting projects with both XML and JSON FHIR resources.
 */
public class JsonSupportTest {

    @TempDir
    Path tempDir;

    /**
     * A "no-op" (no-operation) logger for tests that does nothing.
     */
    private static class NoOpLogger implements Logger {
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
        // Create test project structure
        Path srcMain = tempDir.resolve("src").resolve("main").resolve("resources");
        Path fhirDir = srcMain.resolve("fhir");
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
        DsfLinter.Config config = new DsfLinter.Config(
                tempDir,
                tempDir.resolve("report"),
                false,
                true,
                null,  // mavenGoals
                null,  // skipGoals
                new NoOpLogger()
        );
        DsfLinter linter = new DsfLinter(config);
        assertThrows(IOException.class, linter::lint,
                "Should fail linting for incomplete project structure (Maven build failure)");
    }

    @Test
    void testJsonSupportBehaviorWithMinimalMavenProject() throws IOException {
        createMinimalMavenProject();
        DsfLinter.Config config = new DsfLinter.Config(
                tempDir,
                tempDir.resolve("report"),
                false,
                true,
                null,  // mavenGoals
                null,  // skipGoals
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