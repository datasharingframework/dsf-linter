package dev.dsf.linter;

import dev.dsf.linter.exception.ResourceLinterException;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.service.FhirLintingService;
import dev.dsf.linter.service.LintingResult;
import dev.dsf.linter.util.resource.FhirFileUtils;
import dev.dsf.linter.util.linting.LintingOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import dev.dsf.linter.util.resource.ResourceResolutionResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests to reproduce and verify the lookup behavior of
 * {@code instantiatesCanonical} references in FHIR {@code Task} resources.
 */
public class JsonActivityDefinitionLookupTest
{
    @TempDir
    Path tempDir;

    private FhirLintingService fhirLintingService;
    private File fhirDir;
    private File activityDefinitionDir;

    // FIX: Removed the class-level fhirFiles list to prevent state leakage between tests.
    // Each test will now collect its own files.

    private static class NoOpLogger implements Logger {
        @Override
        public void info(String message) { /* Do nothing */ }
        @Override
        public void warn(String message) { /* Do nothing */ }
        @Override
        public void error(String message) { /* Do nothing */ }
        @Override
        public void error(String message, Throwable throwable) { /* Do nothing */ }
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
    }

    /**
     * Creates a new linter and a directory structure under {@code src/main/resources/fhir}
     * for writing JSON/XML test files before each test.
     */
    @BeforeEach
    void setUp()
    {
        fhirLintingService = new FhirLintingService(new NoOpLogger());

        File projectRoot = tempDir.toFile();
        File srcMain = new File(projectRoot, "src/main/resources");
        fhirDir = new File(srcMain, "fhir");
        activityDefinitionDir = new File(fhirDir, "ActivityDefinition");
        assertTrue(activityDefinitionDir.mkdirs(), "Failed to create directory: " + activityDefinitionDir.getAbsolutePath());
    }

    private List<File> collectFhirFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(fhirDir.toPath())) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(FhirFileUtils::isFhirFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Test case demonstrating that a {@code Task} in JSON format fails to resolve
     * a referenced {@code ActivityDefinition} that is also in JSON format.
     *
     * <p>This test reproduces the unresolved canonical issue and ensures that
     * the bug is detectable by looking for the {@code TASK_UNKNOWN_INSTANTIATES_CANONICAL} error.</p>
     *
     * @throws IOException if file writing or linting fails
     */
    @Test
    void testJsonTaskCannotFindJsonActivityDefinition() throws IOException, ResourceLinterException {
        // Write JSON ActivityDefinition
        String jsonActivityDefinition = """
            {
              "resourceType": "ActivityDefinition",
              "id": "test-process",
              "url": "http://dsf.dev/bpe/Process/testProcess",
              "version": "#{version}",
              "name": "TestProcess",
              "title": "Test Process",
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
                      "url": "message-name",
                      "valueString": "startTestProcess"
                    },
                    {
                      "url": "task-profile",
                      "valueCanonical": "http://dsf.dev/fhir/StructureDefinition/task-start-test-process|#{version}"
                    },
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
        Files.writeString(new File(activityDefinitionDir, "test-activity-definition.json").toPath(), jsonActivityDefinition);

        // Write referencing JSON Task
        String jsonTask = """
            {
              "resourceType": "Task",
              "id": "test-task",
              "meta": {
                "profile": ["http://dsf.dev/fhir/StructureDefinition/dsf-task-base"]
              },
              "instantiatesCanonical": "http://dsf.dev/bpe/Process/testProcess|#{version}",
              "status": "draft",
              "intent": "order",
              "authoredOn": "#{date}",
              "requester": {
                "identifier": {
                  "system": "http://dsf.dev/sid/organization-identifier",
                  "value": "#{organization}"
                }
              },
              "restriction": {
                "recipient": [
                  {
                    "identifier": {
                      "system": "http://dsf.dev/sid/organization-identifier",
                      "value": "#{organization}"
                    }
                  }
                ]
              },
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
                  "valueString": "startTestProcess"
                }
              ]
            }""";
        Files.writeString(new File(fhirDir, "test-task.json").toPath(), jsonTask);
        List<File> fhirFiles = collectFhirFiles();
        List<String> missingRefs = new ArrayList<>();
        Map<String, ResourceResolutionResult> outsideRoot = new HashMap<>();
        Map<String, ResourceResolutionResult> fromDependencies = new HashMap<>();
        LintingResult lintingResult =
                fhirLintingService.lint("test-plugin", fhirFiles, missingRefs, outsideRoot, fromDependencies, fhirDir);

        // FIX: getItems() is now called on the top-level LintingResult object
        List<AbstractLintItem> items = lintingResult.getItems();
        LintingOutput result = new LintingOutput(items);

        System.out.println("Total lint items: " + result.LintItems().size());
        result.LintItems().forEach(item -> System.out.println("* " + item));

        boolean hasUnknownCanonicalError = result.LintItems().stream()
                .anyMatch(item -> item.toString().contains("TASK_UNKNOWN_INSTANTIATES_CANONICAL"));
        assertFalse(hasUnknownCanonicalError,
                "Linting should succeed without a TASK_UNKNOWN_INSTANTIATES_CANONICAL error.");
    }
    /**
     * Test case demonstrating that a {@code Task} in JSON format correctly resolves
     * a referenced {@code ActivityDefinition} when it is provided in XML format.
     *
     * <p>This verifies that the lookup logic works correctly for XML resources,
     * and the canonical reference is resolved successfully.</p>
     *
     * @throws IOException if file writing or linting fails
     */
    @Test
    void testJsonTaskCanFindXmlActivityDefinition() throws IOException, ResourceLinterException {
        // Write XML ActivityDefinition
        String xmlActivityDefinition = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ActivityDefinition xmlns="http://hl7.org/fhir">
              <id value="test-process"/>
              <url value="http://dsf.dev/bpe/Process/testProcess"/>
              <version value="#{version}"/>
              <name value="TestProcess"/>
              <title value="Test Process"/>
              <status value="unknown"/>
              <kind value="Task"/>
              <meta>
                <tag>
                  <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                  <code value="ALL"/>
                </tag>
              </meta>
              <extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization">
                <extension url="message-name">
                  <valueString value="startTestProcess"/>
                </extension>
                <extension url="task-profile">
                  <valueCanonical value="http://dsf.dev/fhir/StructureDefinition/task-start-test-process|#{version}"/>
                </extension>
                <extension url="requester">
                  <valueCoding>
                    <system value="http://dsf.dev/fhir/CodeSystem/process-authorization"/>
                    <code value="LOCAL_ALL"/>
                  </valueCoding>
                </extension>
                <extension url="recipient">
                  <valueCoding>
                    <system value="http://dsf.dev/fhir/CodeSystem/process-authorization"/>
                    <code value="LOCAL_ALL"/>
                  </valueCoding>
                </extension>
              </extension>
            </ActivityDefinition>""";
        Files.writeString(new File(activityDefinitionDir, "test-activity-definition.xml").toPath(), xmlActivityDefinition);

        // Write referencing JSON Task
        String jsonTask = """
            {
              "resourceType": "Task",
              "id": "test-task",
              "meta": {
                "profile": ["http://dsf.dev/fhir/StructureDefinition/dsf-task-base"]
              },
              "instantiatesCanonical": "http://dsf.dev/bpe/Process/testProcess|#{version}",
              "status": "draft",
              "intent": "order",
              "authoredOn": "#{date}",
              "requester": {
                "identifier": {
                  "system": "http://dsf.dev/sid/organization-identifier",
                  "value": "#{organization}"
                }
              },
              "restriction": {
                "recipient": [
                  {
                    "identifier": {
                      "system": "http://dsf.dev/sid/organization-identifier",
                      "value": "#{organization}"
                    }
                  }
                ]
              },
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
                  "valueString": "startTestProcess"
                }
              ]
            }""";
        Files.writeString(new File(fhirDir, "test-task.json").toPath(), jsonTask);
        List<File> fhirFiles = collectFhirFiles();
        List<String> missingRefs = new ArrayList<>();
        Map<String, ResourceResolutionResult> outsideRoot = new HashMap<>();
        Map<String, ResourceResolutionResult> fromDependencies = new HashMap<>();
        LintingResult lintingResult =
                fhirLintingService.lint("test-plugin", fhirFiles, missingRefs, outsideRoot, fromDependencies, fhirDir);

        List<AbstractLintItem> items = lintingResult.getItems();
        LintingOutput result = new LintingOutput(items);

        System.out.println("Total lint items: " + result.LintItems().size());
        result.LintItems().forEach(item -> System.out.println("* " + item));

        boolean hasUnknownCanonicalError = result.LintItems().stream()
                .anyMatch(item -> item.toString().contains("TASK_UNKNOWN_INSTANTIATES_CANONICAL"));
        assertFalse(hasUnknownCanonicalError,
                "Should not have TASK_UNKNOWN_INSTANTIATES_CANONICAL error when XML ActivityDefinition exists");
    }
}