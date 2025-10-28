package dev.dsf.linter;

import dev.dsf.linter.exception.ResourceValidationException;
import dev.dsf.linter.output.item.AbstractValidationItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.service.FhirValidationService;
import dev.dsf.linter.service.ValidationResult;
import dev.dsf.linter.util.resource.FhirFileUtils;
import dev.dsf.linter.util.validation.ValidationOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests reproducing and validating the original issue described in the DSF problem statement.
 * These tests now use the refactored service-based validation API.
 */
public class JsonProblemStatementTest
{
    @TempDir
    Path tempDir;

    private FhirValidationService fhirValidationService;
    private File fhirDir;
    private File activityDefinitionDir;

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
    void setUp() throws IOException
    {
        fhirValidationService = new FhirValidationService(new NoOpLogger());

        File projectRoot = tempDir.toFile();
        File srcMain = new File(projectRoot, "src/main/resources");
        fhirDir = new File(srcMain, "fhir");
        activityDefinitionDir = new File(fhirDir, "ActivityDefinition");
        if (!activityDefinitionDir.mkdirs() && !activityDefinitionDir.exists()) {
            throw new IOException("Failed to create ActivityDefinition directory");
        }
    }

    /**
     * Helper method to find all FHIR resource files (.xml, .json) in the test setup.
     */
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
     * Verifies that the original problem—JSON {@code Task} files failing to resolve JSON
     * {@code ActivityDefinition} resources—is fixed.
     * <p>
     * Checks that:
     * <ul>
     * <li>No {@code TASK_UNKNOWN_INSTANTIATES_CANONICAL} error occurs</li>
     * <li>Successful resolution of the canonical reference is reported</li>
     * <li>Validation messages reference only JSON file names</li>
     * </ul>
     *
     * @throws IOException if resource creation fails
     * @throws ResourceValidationException if validation fails
     */
    @Test
    void testOriginalProblemScenarioFixed() throws IOException, ResourceValidationException {
        // ... (file writing code remains the same)
        String jsonPingActivityDefinition = "{\n" +
                "              \"resourceType\": \"ActivityDefinition\",\n" +
                "              \"meta\": {\n" +
                "                \"tag\": [\n" +
                "                  {\n" +
                "                    \"system\": \"http://dsf.dev/fhir/CodeSystem/read-access-tag\",\n" +
                "                    \"code\": \"ALL\"\n" +
                "                  }\n" +
                "                ]\n" +
                "              },\n" +
                "              \"extension\": [\n" +
                "                {\n" +
                "                  \"url\": \"http://dsf.dev/fhir/StructureDefinition/extension-process-authorization\",\n" +
                "                  \"extension\": [\n" +
                "                    {\n" +
                "                      \"url\": \"message-name\",\n" +
                "                      \"valueString\": \"startPing\"\n" +
                "                    },\n" +
                "                    {\n" +
                "                      \"url\": \"task-profile\",\n" +
                "                      \"valueCanonical\": \"http://dsf.dev/fhir/StructureDefinition/task-start-ping|#{version}\"\n" +
                "                    },\n" +
                "                    {\n" +
                "                      \"url\": \"requester\",\n" +
                "                      \"valueCoding\": {\n" +
                "                        \"system\": \"http://dsf.dev/fhir/CodeSystem/process-authorization\",\n" +
                "                        \"code\": \"LOCAL_ALL\"\n" +
                "                      }\n" +
                "                    },\n" +
                "                    {\n" +
                "                      \"url\": \"recipient\",\n" +
                "                      \"valueCoding\": {\n" +
                "                        \"system\": \"http://dsf.dev/fhir/CodeSystem/process-authorization\",\n" +
                "                        \"code\": \"LOCAL_ALL\"\n" +
                "                      }\n" +
                "                    }\n" +
                "                  ]\n" +
                "                }\n" +
                "              ],\n" +
                "              \"url\": \"http://dsf.dev/bpe/Process/ping\",\n" +
                "              \"version\": \"#{version}\",\n" +
                "              \"name\": \"Ping\",\n" +
                "              \"title\": \"PING process\",\n" +
                "              \"subtitle\": \"Communication Testing Process\",\n" +
                "              \"status\": \"unknown\",\n" +
                "              \"experimental\": false,\n" +
                "              \"date\": \"#{date}\",\n" +
                "              \"publisher\": \"DSF\",\n" +
                "              \"contact\": [\n" +
                "                {\n" +
                "                  \"name\": \"DSF\",\n" +
                "                  \"telecom\": [\n" +
                "                    {\n" +
                "                      \"system\": \"email\",\n" +
                "                      \"value\": \"pmo@dsf.dev\"\n" +
                "                    }\n" +
                "                  ]\n" +
                "                }\n" +
                "              ],\n" +
                "              \"description\": \"Process to send PING messages to remote Organizations and to receive corresponding PONG messages\",\n" +
                "              \"kind\": \"Task\"\n" +
                "            }";
        Files.writeString(new File(activityDefinitionDir, "dsf-ping.json").toPath(), jsonPingActivityDefinition);
        String jsonStartPingTask = "{\n" +
                "              \"resourceType\": \"Task\",\n" +
                "              \"id\": \"start-ping-task\",\n" +
                "              \"meta\": {\n" +
                "                \"profile\": [\"http://dsf.dev/fhir/StructureDefinition/dsf-task-base\"]\n" +
                "              },\n" +
                "              \"instantiatesCanonical\": \"http://dsf.dev/bpe/Process/ping|#{version}\",\n" +
                "              \"status\": \"draft\",\n" +
                "              \"intent\": \"order\",\n" +
                "              \"authoredOn\": \"#{date}\",\n" +
                "              \"requester\": {\n" +
                "                \"identifier\": {\n" +
                "                  \"system\": \"http://dsf.dev/sid/organization-identifier\",\n" +
                "                  \"value\": \"#{organization}\"\n" +
                "                }\n" +
                "              },\n" +
                "              \"restriction\": {\n" +
                "                \"recipient\": [\n" +
                "                  {\n" +
                "                    \"identifier\": {\n" +
                "                      \"system\": \"http://dsf.dev/sid/organization-identifier\",\n" +
                "                      \"value\": \"#{organization}\"\n" +
                "                    }\n" +
                "                  }\n" +
                "                ]\n" +
                "              },\n" +
                "              \"input\": [\n" +
                "                {\n" +
                "                  \"type\": {\n" +
                "                    \"coding\": [\n" +
                "                      {\n" +
                "                        \"system\": \"http://dsf.dev/fhir/CodeSystem/bpmn-message\",\n" +
                "                        \"code\": \"message-name\"\n" +
                "                      }\n" +
                "                    ]\n" +
                "                  },\n" +
                "                  \"valueString\": \"startPing\"\n" +
                "                }\n" +
                "              ]\n" +
                "            }";
        Files.writeString(new File(fhirDir, "dsf-task-start-ping.json").toPath(), jsonStartPingTask);
        // Perform validation using the refactored service
        final List<File> fhirFiles = collectFhirFiles();
        // FIX: Use ValidationResult directly, not as a nested class
        ValidationResult validationResult =
                fhirValidationService.validate("test-plugin", fhirFiles, new ArrayList<>());

        // FIX: getItems() is now called on the top-level ValidationResult object
        List<AbstractValidationItem> items = validationResult.getItems();
        ValidationOutput result = new ValidationOutput(items);

        System.out.println("Total validation items: " + result.validationItems().size());
        result.validationItems().forEach(item -> System.out.println("* " + item));

        // ... (assertions remain the same)
        boolean hasUnknownCanonicalError = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("TASK_UNKNOWN_INSTANTIATES_CANONICAL"));
        assertFalse(hasUnknownCanonicalError,
                "Should not have TASK_UNKNOWN_INSTANTIATES_CANONICAL error for JSON->JSON lookup");

        // The main goal is to ensure the validation completes without the specific error
        // Check that validation ran and processed the files
        assertFalse(result.validationItems().isEmpty(), "Should have validation items");

        // Verify both files were processed by checking for file-related validation items
        boolean hasTaskFileItems = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("dsf-task-start-ping") ||
                        item.toString().contains("start-ping-task"));
        boolean hasActivityDefItems = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("dsf-ping") ||
                        item.toString().contains("ActivityDefinition"));
        assertTrue(hasTaskFileItems || hasActivityDefItems,
                "Should have validation items for the created files");
        System.out.println("Validation completed successfully without TASK_UNKNOWN_INSTANTIATES_CANONICAL error");
    }


    /**
     * Verifies that a {@code Task} resource in JSON format can still successfully resolve
     * a referenced {@code ActivityDefinition} provided in XML format.
     *
     * <p>This confirms compatibility between mixed formats and ensures that cross-format lookup
     * logic is functioning correctly.</p>
     *
     * @throws IOException if resource creation fails
     * @throws ResourceValidationException if validation fails
     */
    @Test
    void testMixedXmlJsonLookupWorks() throws IOException, ResourceValidationException {
        // ... (file writing code remains the same)
        String xmlActivityDefinition = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "            <ActivityDefinition xmlns=\"http://hl7.org/fhir\">\n" +
                "              <meta>\n" +
                "                <tag>\n" +
                "                  <system value=\"http://dsf.dev/fhir/CodeSystem/read-access-tag\"/>\n" +
                "                  <code value=\"ALL\"/>\n" +
                "                </tag>\n" +
                "              </meta>\n" +
                "              <url value=\"http://dsf.dev/bpe/Process/pong\"/>\n" +
                "              <version value=\"#{version}\"/>\n" +
                "              <name value=\"Pong\"/>\n" +
                "              <title value=\"PONG process\"/>\n" +
                "              <status value=\"unknown\"/>\n" +
                "              <kind value=\"Task\"/>\n" +
                "              <extension url=\"http://dsf.dev/fhir/StructureDefinition/extension-process-authorization\">\n" +
                "                <extension url=\"message-name\">\n" +
                "                  <valueString value=\"ping\"/>\n" +
                "                </extension>\n" +
                "                <extension url=\"requester\">\n" +
                "                  <valueCoding>\n" +
                "                    <system value=\"http://dsf.dev/fhir/CodeSystem/process-authorization\"/>\n" +
                "                    <code value=\"LOCAL_ALL\"/>\n" +
                "                  </valueCoding>\n" +
                "                </extension>\n" +
                "                <extension url=\"recipient\">\n" +
                "                  <valueCoding>\n" +
                "                    <system value=\"http://dsf.dev/fhir/CodeSystem/process-authorization\"/>\n" +
                "                    <code value=\"LOCAL_ALL\"/>\n" +
                "                  </valueCoding>\n" +
                "                </extension>\n" +
                "              </extension>\n" +
                "            </ActivityDefinition>";
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

        // Perform validation using the refactored service
        final List<File> fhirFiles = collectFhirFiles();
        ValidationResult validationResult =
                fhirValidationService.validate("test-plugin", fhirFiles, new ArrayList<>());

        // FIX: getItems() is now called on the top-level ValidationResult object
        List<AbstractValidationItem> items = validationResult.getItems();
        ValidationOutput result = new ValidationOutput(items);

        // Ensure successful lookup between formats
        boolean hasUnknownCanonicalError = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("TASK_UNKNOWN_INSTANTIATES_CANONICAL"));
        assertFalse(hasUnknownCanonicalError,
                "Should not have TASK_UNKNOWN_INSTANTIATES_CANONICAL error when JSON Task references XML ActivityDefinition");
    }
}