package dev.dsf.utils.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class JsonSupportTest {

    @TempDir
    Path tempDir;

    private DsfValidatorImpl validator;

    @BeforeEach
    void setUp() throws IOException {
        validator = new DsfValidatorImpl();

        // Create test project structure
        File projectRoot = tempDir.toFile();
        File srcMain = new File(projectRoot, "src/main/resources");
        File fhirDir = new File(srcMain, "fhir");
        fhirDir.mkdirs();

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
        Files.writeString(new File(fhirDir, "test-task.xml").toPath(), xmlContent);

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
        Files.writeString(new File(fhirDir, "test-task.json").toPath(), jsonContent);
    }

    @Test
    void testCurrentBehaviorWithXmlAndJson() {
        // This test demonstrates that JSON support now works
        ValidationOutput result = validator.validate(tempDir);

        System.out.println("Total validation items: " + result.validationItems().size());

        // Count issues by file type
        long xmlIssues = result.validationItems().stream()
                .filter(item -> item.toString().contains("test-task.xml"))
                .count();
        long jsonIssues = result.validationItems().stream()
                .filter(item -> item.toString().contains("test-task.json"))
                .count();

        System.out.println("XML file issues: " + xmlIssues);
        System.out.println("JSON file issues: " + jsonIssues);

        // Both XML and JSON files should now be processed
        assertTrue(xmlIssues > 0, "Should have validation items from XML file");
        assertTrue(jsonIssues > 0, "Should have validation items from JSON file");

        // They should have the same number of issues since they contain the same data
        assertEquals(xmlIssues, jsonIssues, "XML and JSON files should produce the same number of validation items");

        // Check what files were actually processed in the report directory
        File reportDir = new File("report");
        if (reportDir.exists()) {
            System.out.println("Report directory contents:");
            printDirectoryContents(reportDir, "  ");
        }
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