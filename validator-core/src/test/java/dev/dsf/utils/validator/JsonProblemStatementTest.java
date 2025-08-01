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
 * Integration tests reproducing and validating the original issue described in the DSF problem statement:
 * <p>
 * A {@code Task} resource in JSON format referencing an {@code ActivityDefinition} in JSON format
 * failed to resolve the {@code instantiatesCanonical} reference. These tests verify that the issue
 * is resolved and that lookups work consistently across JSON and XML representations.
 * </p>
 * <p>
 * Tests also ensure that validation messages are consistent with file formats and do not mix
 * references between JSON and XML sources.
 * </p>
 */
public class JsonProblemStatementTest
{
    /**
     * Temporary working directory for isolated resource creation.
     */
    @TempDir
    Path tempDir;

    /**
     * The DSF validator under test.
     */
    private DsfValidatorImpl validator;

    /**
     * Directory to store test FHIR resources.
     */
    private File fhirDir;

    /**
     * Subdirectory under {@code fhir/ActivityDefinition} for process definitions.
     */
    private File activityDefinitionDir;

    /**
     * Sets up the test directory structure and instantiates the validator before each test.
     *
     * @throws IOException if temporary files cannot be created
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
     * Verifies that the original problem—JSON {@code Task} files failing to resolve JSON
     * {@code ActivityDefinition} resources—is fixed.
     * <p>
     * Checks that:
     * <ul>
     *   <li>No {@code TASK_UNKNOWN_INSTANTIATES_CANONICAL} error occurs</li>
     *   <li>Successful resolution of the canonical reference is reported</li>
     *   <li>Validation messages reference only JSON file names</li>
     * </ul>
     *
     * @throws IOException if resource creation fails
     */
    @Test
    void testOriginalProblemScenarioFixed() throws IOException
    {
        // Write JSON ActivityDefinition
        String jsonPingActivityDefinition = """
            {
              "resourceType": "ActivityDefinition",
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
                      "valueString": "startPing"
                    },
                    {
                      "url": "task-profile",
                      "valueCanonical": "http://dsf.dev/fhir/StructureDefinition/task-start-ping|#{version}"
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
              ],
              "url": "http://dsf.dev/bpe/Process/ping",
              "version": "#{version}",
              "name": "Ping",
              "title": "PING process",
              "subtitle": "Communication Testing Process",
              "status": "unknown",
              "experimental": false,
              "date": "#{date}",
              "publisher": "DSF",
              "contact": [
                {
                  "name": "DSF",
                  "telecom": [
                    {
                      "system": "email",
                      "value": "pmo@dsf.dev"
                    }
                  ]
                }
              ],
              "description": "Process to send PING messages to remote Organizations and to receive corresponding PONG messages",
              "kind": "Task"
            }""";
        Files.writeString(new File(activityDefinitionDir, "dsf-ping.json").toPath(), jsonPingActivityDefinition);

        // Write JSON Task that references the ActivityDefinition
        String jsonStartPingTask = """
            {
              "resourceType": "Task",
              "id": "start-ping-task",
              "meta": {
                "profile": ["http://dsf.dev/fhir/StructureDefinition/dsf-task-base"]
              },
              "instantiatesCanonical": "http://dsf.dev/bpe/Process/ping|#{version}",
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
                  "valueString": "startPing"
                }
              ]
            }""";
        Files.writeString(new File(fhirDir, "dsf-task-start-ping.json").toPath(), jsonStartPingTask);

        // Perform validation
        ValidationOutput result = validator.validate(tempDir);

        System.out.println("Total validation items: " + result.validationItems().size());
        result.validationItems().forEach(item -> System.out.println("* " + item));

        // Ensure the original issue is fixed
        boolean hasUnknownCanonicalError = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("TASK_UNKNOWN_INSTANTIATES_CANONICAL"));
        assertFalse(hasUnknownCanonicalError,
                "Should not have TASK_UNKNOWN_INSTANTIATES_CANONICAL error when JSON ActivityDefinition exists for JSON Task");

        // Verify successful lookup
        boolean hasActivityDefinitionExists = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("ActivityDefinition exists"));
        assertTrue(hasActivityDefinitionExists,
                "Should have 'ActivityDefinition exists' success message when JSON ActivityDefinition is found");

        // Check that no errors mention .xml files for a .json input
        boolean hasIncorrectXmlFilenameInJsonError = result.validationItems().stream()
                .filter(item -> item.toString().contains("dsf-task-start-ping.json"))
                .anyMatch(item -> item.toString().contains("dsf-task-start-ping.xml"));
        assertFalse(hasIncorrectXmlFilenameInJsonError,
                "Error messages for JSON files should not reference XML filenames");
    }

    /**
     * Verifies that a {@code Task} resource in JSON format can still successfully resolve
     * a referenced {@code ActivityDefinition} provided in XML format.
     *
     * <p>This confirms compatibility between mixed formats and ensures that cross-format lookup
     * logic is functioning correctly.</p>
     *
     * @throws IOException if resource creation fails
     */
    @Test
    void testMixedXmlJsonLookupWorks() throws IOException
    {
        // Write XML ActivityDefinition
        String xmlActivityDefinition = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ActivityDefinition xmlns="http://hl7.org/fhir">
              <meta>
                <tag>
                  <system value="http://dsf.dev/fhir/CodeSystem/read-access-tag"/>
                  <code value="ALL"/>
                </tag>
              </meta>
              <url value="http://dsf.dev/bpe/Process/pong"/>
              <version value="#{version}"/>
              <name value="Pong"/>
              <title value="PONG process"/>
              <status value="unknown"/>
              <kind value="Task"/>
              <extension url="http://dsf.dev/fhir/StructureDefinition/extension-process-authorization">
                <extension url="message-name">
                  <valueString value="ping"/>
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
        Files.writeString(new File(activityDefinitionDir, "dsf-pong.xml").toPath(), xmlActivityDefinition);

        // Write referencing JSON Task
        String jsonTask = """
            {
              "resourceType": "Task",
              "id": "pong-task",
              "meta": {
                "profile": ["http://dsf.dev/fhir/StructureDefinition/dsf-task-base"]
              },
              "instantiatesCanonical": "http://dsf.dev/bpe/Process/pong|#{version}",
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
                  "valueString": "ping"
                }
              ]
            }""";
        Files.writeString(new File(fhirDir, "pong-task.json").toPath(), jsonTask);

        // Perform validation
        ValidationOutput result = validator.validate(tempDir);

        // Ensure successful lookup between formats
        boolean hasUnknownCanonicalError = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("TASK_UNKNOWN_INSTANTIATES_CANONICAL"));
        assertFalse(hasUnknownCanonicalError,
                "Should not have TASK_UNKNOWN_INSTANTIATES_CANONICAL error when JSON Task references XML ActivityDefinition");
    }
}
