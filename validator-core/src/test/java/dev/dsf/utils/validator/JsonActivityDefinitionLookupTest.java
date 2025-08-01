package dev.dsf.utils.validator;

import dev.dsf.utils.validator.util.ValidationOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests to reproduce and verify the lookup behavior of
 * {@code instantiatesCanonical} references in FHIR {@code Task} resources.
 *
 * <p>This test specifically checks whether a {@code Task} in JSON format
 * can resolve a referenced {@code ActivityDefinition} resource in:
 * <ul>
 *   <li>JSON format (expected to fail due to a known issue)</li>
 *   <li>XML format (expected to succeed)</li>
 * </ul>
 *
 * <p>See issue report related to {@code TASK_UNKNOWN_INSTANTIATES_CANONICAL}
 * errors when working with all-JSON resource inputs.</p>
 */
public class JsonActivityDefinitionLookupTest
{
    /**
     * Temporary directory provided by JUnit for isolating test resources.
     */
    @TempDir
    Path tempDir;

    /**
     * Validator instance used to perform resource validation.
     */
    private DsfValidatorImpl validator;

    /**
     * Directory where FHIR test resources will be written.
     */
    private File fhirDir;

    /**
     * Subdirectory for storing ActivityDefinition files (JSON or XML).
     */
    private File activityDefinitionDir;

    /**
     * Creates a new validator and a directory structure under {@code src/main/resources/fhir}
     * for writing JSON/XML test files before each test.
     *
     * @throws IOException if test directory creation fails
     */
    @BeforeEach
    void setUp() throws IOException
    {
        validator = new DsfValidatorImpl();

        File projectRoot = tempDir.toFile();
        File srcMain = new File(projectRoot, "src/main/resources");
        fhirDir = new File(srcMain, "fhir");
        activityDefinitionDir = new File(fhirDir, "ActivityDefinition");
        activityDefinitionDir.mkdirs();
    }

    /**
     * Test case demonstrating that a {@code Task} in JSON format fails to resolve
     * a referenced {@code ActivityDefinition} that is also in JSON format.
     *
     * <p>This test reproduces the unresolved canonical issue and ensures that
     * the bug is detectable by looking for the {@code TASK_UNKNOWN_INSTANTIATES_CANONICAL} error.</p>
     *
     * @throws IOException if file writing or validation fails
     */
    @Test
    void testJsonTaskCannotFindJsonActivityDefinition() throws IOException
    {
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

        ValidationOutput result = validator.validate(tempDir);

        // Print result for debugging
        System.out.println("Total validation items: " + result.validationItems().size());
        result.validationItems().forEach(item -> System.out.println("* " + item));

        // Expectation: Error should NOT appear, but bug causes it
        boolean hasUnknownCanonicalError = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("TASK_UNKNOWN_INSTANTIATES_CANONICAL"));

        assertFalse(hasUnknownCanonicalError,
                "Should not have TASK_UNKNOWN_INSTANTIATES_CANONICAL error when JSON ActivityDefinition exists");
    }

    /**
     * Test case demonstrating that a {@code Task} in JSON format correctly resolves
     * a referenced {@code ActivityDefinition} when it is provided in XML format.
     *
     * <p>This verifies that the lookup logic works correctly for XML resources,
     * and the canonical reference is resolved successfully.</p>
     *
     * @throws IOException if file writing or validation fails
     */
    @Test
    void testJsonTaskCanFindXmlActivityDefinition() throws IOException
    {
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

        ValidationOutput result = validator.validate(tempDir);

        // Print result for debugging
        System.out.println("Total validation items: " + result.validationItems().size());
        result.validationItems().forEach(item -> System.out.println("* " + item));

        // Expectation: No error should appear, XML reference should resolve correctly
        boolean hasUnknownCanonicalError = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("TASK_UNKNOWN_INSTANTIATES_CANONICAL"));

        assertFalse(hasUnknownCanonicalError,
                "Should not have TASK_UNKNOWN_INSTANTIATES_CANONICAL error when XML ActivityDefinition exists");
    }
}
