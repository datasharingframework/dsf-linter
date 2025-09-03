package dev.dsf.utils.validator;

import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;
import dev.dsf.utils.validator.exception.ResourceValidationException;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.util.validation.ValidationOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>
 * Comprehensive test suite for {@link DsfValidatorImpl} covering directory validation,
 * Maven project structure, and ProcessPluginDefinition discovery.
 * </p>
 *
 * <p>
 * The tests are organized into nested classes:
 * <ul>
 *   <li>{@code DirectoryValidation} - Tests for project directory validation</li>
 *   <li>{@code ErrorHandling} - Edge cases and error scenarios</li>
 *   <li>{@code ProjectStructure} - Tests for different project structures</li>
 * </ul>
 * </p>
 *
 * <h2>Note</h2>
 * <p>
 * The current implementation of {@link DsfValidatorImpl} only supports directory validation
 * and requires a proper Maven project structure with a {@code ProcessPluginDefinition}.
 * Single file validation is not supported.
 * </p>
 */
class TestDsfValidatorImpl
{
    private final DsfValidatorImpl validator = new DsfValidatorImpl(new NoOpLogger());

    /**
     * A "no-op" logger for tests that does nothing.
     * This keeps test console output clean.
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

    @Nested
    @DisplayName("Directory Validation")
    class DirectoryValidation
    {
        @Test
        @DisplayName("Should return empty output for unsupported file types")
        void testUnsupportedFileType(@TempDir Path tempDir) throws IOException, MissingServiceRegistrationException, ResourceValidationException, InterruptedException {
            Path txtFile = tempDir.resolve("test.txt");
            Files.writeString(txtFile, "This is a text file");

            ValidationOutput output = validator.validate(txtFile);

            assertNotNull(output, "Validation output should not be null");
            assertTrue(output.validationItems().isEmpty(),
                    "Should return empty validation items for unsupported file types");
        }

        @Test
        @DisplayName("Should handle empty project directory")
        void testEmptyProjectDirectory(@TempDir Path tempDir) {
            // The current implementation expects a Maven project with ProcessPluginDefinition
            // An empty directory should fail during the Maven build step
            assertThrows(RuntimeException.class, () -> validator.validate(tempDir),
                    "Empty project should fail during Maven build");
        }

        @Test
        @DisplayName("Should handle directory without pom.xml")
        void testDirectoryWithoutPom(@TempDir Path tempDir) throws IOException {
            createBasicProjectStructure(tempDir);

            // Without pom.xml, Maven build should fail
            assertThrows(RuntimeException.class, () -> validator.validate(tempDir),
                    "Project without pom.xml should fail during Maven build");
        }

        @Test
        @DisplayName("Should handle project with minimal Maven structure")
        void testMinimalMavenProject(@TempDir Path tempDir) throws IOException {
            createMinimalMavenProject(tempDir);

            // This should fail because there's no ProcessPluginDefinition
            assertThrows(IllegalStateException.class, () -> validator.validate(tempDir),
                    "Project without ProcessPluginDefinition should fail during discovery");
        }
    }

    @Nested
    @DisplayName("Project Structure Tests")
    class ProjectStructure
    {
        @Test
        @DisplayName("Should handle project with only BPMN files")
        void testProjectWithOnlyBpmnFiles(@TempDir Path tempDir) throws IOException {
            createBpmnOnlyProjectStructure(tempDir);

            // Should fail because no ProcessPluginDefinition is present
            assertThrows(IllegalStateException.class, () -> validator.validate(tempDir),
                    "Project without ProcessPluginDefinition should fail");
        }

        @Test
        @DisplayName("Should handle project with only FHIR files")
        void testProjectWithOnlyFhirFiles(@TempDir Path tempDir) throws IOException {
            createFhirOnlyProjectStructure(tempDir);

            // Should fail because no ProcessPluginDefinition is present
            assertThrows(IllegalStateException.class, () -> validator.validate(tempDir),
                    "Project without ProcessPluginDefinition should fail");
        }

        @Test
        @DisplayName("Should handle project with complete structure but no ProcessPluginDefinition")
        void testCompleteProjectWithoutPluginDefinition(@TempDir Path tempDir) throws IOException {
            createCompleteProjectStructure(tempDir);

            // Should fail during ProcessPluginDefinition discovery
            assertThrows(IllegalStateException.class, () -> validator.validate(tempDir),
                    "Project without ProcessPluginDefinition should fail during discovery");
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandling
    {
        @Test
        @DisplayName("Should handle non-existent directory")
        void testNonExistentDirectory(@TempDir Path tempDir) throws IOException, MissingServiceRegistrationException, ResourceValidationException, InterruptedException {
            Path nonExistentDir = tempDir.resolve("does-not-exist");

            ValidationOutput output = validator.validate(nonExistentDir);

            assertNotNull(output, "Should return validation output even for non-existent directories");
            assertTrue(output.validationItems().isEmpty(),
                      "Should return empty validation items for non-existent directories");
        }

        @Test
        @DisplayName("Should handle single file input")
        void testSingleFileInput(@TempDir Path tempDir) throws IOException, MissingServiceRegistrationException, ResourceValidationException, InterruptedException {
            Path bpmnFile = createValidBpmnFile(tempDir, "test.bpmn");

            ValidationOutput output = validator.validate(bpmnFile);

            assertNotNull(output, "Should return validation output");
            assertTrue(output.validationItems().isEmpty(),
                      "Should return empty validation items for single files");
        }

        @Test
        @DisplayName("Should handle corrupted pom.xml")
        void testCorruptedPomXml(@TempDir Path tempDir) throws IOException {
            createBasicProjectStructure(tempDir);
            Files.writeString(tempDir.resolve("pom.xml"), "invalid xml content");

            // Should fail during Maven build
            assertThrows(RuntimeException.class, () -> validator.validate(tempDir),
                    "Project with corrupted pom.xml should fail during Maven build");
        }
    }

    // Utility methods for test setup

    private void createBasicProjectStructure(Path tempDir) throws IOException
    {
        // Create basic Maven directory structure
        Path srcMainJava = tempDir.resolve("src").resolve("main").resolve("java");
        Path srcMainResources = tempDir.resolve("src").resolve("main").resolve("resources");
        Files.createDirectories(srcMainJava);
        Files.createDirectories(srcMainResources);
    }

    private void createMinimalMavenProject(Path tempDir) throws IOException
    {
        createBasicProjectStructure(tempDir);

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

    private void createValidFhirJsonFile(Path directory, String filename) throws IOException
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
    }

    private void createValidFhirXmlFile(Path directory, String filename) throws IOException
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
    }

    private void createCompleteProjectStructure(Path tempDir) throws IOException
    {
        createMinimalMavenProject(tempDir);

        // Create BPMN structure
        Path bpmnDir = createBpmnDirectory(tempDir);
        createValidBpmnFile(bpmnDir, "process1.bpmn");
        createValidBpmnFile(bpmnDir, "process2.bpmn");

        // Create FHIR structure
        Path fhirDir = createFhirDirectory(tempDir);
        createValidFhirJsonFile(fhirDir, "task.json");
        createValidFhirXmlFile(fhirDir, "valueset.xml");
    }

    private void createBpmnOnlyProjectStructure(Path tempDir) throws IOException
    {
        createMinimalMavenProject(tempDir);
        Path bpmnDir = createBpmnDirectory(tempDir);
        createValidBpmnFile(bpmnDir, "process.bpmn");
    }

    private void createFhirOnlyProjectStructure(Path tempDir) throws IOException
    {
        createMinimalMavenProject(tempDir);
        Path fhirDir = createFhirDirectory(tempDir);
        createValidFhirJsonFile(fhirDir, "task.json");
    }
}
