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

public class JsonSupportAdditionalTest {

    @TempDir
    Path tempDir;

    private DsfValidatorImpl validator;
    private File fhirDir;

    @BeforeEach
    void setUp() throws IOException {
        validator = new DsfValidatorImpl();

        // Create test project structure
        File projectRoot = tempDir.toFile();
        File srcMain = new File(projectRoot, "src/main/resources");
        fhirDir = new File(srcMain, "fhir");
        fhirDir.mkdirs();
    }

    @Test
    void testJsonSupportForCodeSystem() throws IOException {
        // Create JSON CodeSystem
        String jsonCodeSystem = """
            {
              "resourceType": "CodeSystem",
              "id": "test-codesystem",
              "url": "http://dsf.dev/fhir/CodeSystem/test",
              "version": "1.0.0",
              "name": "TestCodeSystem",
              "title": "Test Code System",
              "status": "unknown",
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

        Files.writeString(new File(fhirDir, "test-codesystem.json").toPath(), jsonCodeSystem);

        // Validate
        ValidationOutput result = validator.validate(tempDir);

        // Should process the JSON file
        assertFalse(result.validationItems().isEmpty(), "Should have validation items from JSON CodeSystem");

        // Should find some validation items for the CodeSystem
        boolean hasCodeSystemValidation = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("test-codesystem.json"));

        assertTrue(hasCodeSystemValidation, "Should have validation items for the JSON CodeSystem file");
    }

    @Test
    void testJsonSupportForValueSet() throws IOException {
        // Create JSON ValueSet
        String jsonValueSet = """
            {
              "resourceType": "ValueSet",
              "id": "test-valueset",
              "url": "http://dsf.dev/fhir/ValueSet/test",
              "version": "1.0.0",
              "name": "TestValueSet",
              "title": "Test Value Set",
              "status": "unknown",
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

        Files.writeString(new File(fhirDir, "test-valueset.json").toPath(), jsonValueSet);

        // Validate
        ValidationOutput result = validator.validate(tempDir);

        // Should process the JSON file
        assertFalse(result.validationItems().isEmpty(), "Should have validation items from JSON ValueSet");

        // Should find some validation items for the ValueSet
        boolean hasValueSetValidation = result.validationItems().stream()
                .anyMatch(item -> item.toString().contains("test-valueset.json"));

        assertTrue(hasValueSetValidation, "Should have validation items for the JSON ValueSet file");
    }
}