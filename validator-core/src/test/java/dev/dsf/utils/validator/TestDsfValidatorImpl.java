package dev.dsf.utils.validator;

import dev.dsf.utils.validator.bpmn.BpmnModelValidator;
import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.UnparsableBpmnFileValidationItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link DsfValidatorImpl} class responsible for reading BPMN files and delegating
 * the validation to a {@link BpmnModelValidator}.
 * <p>
 * This test class contains nested test classes that check:
 * <ul>
 *   <li>File existence and readability (ensuring that the validator handles missing or unreadable files appropriately).</li>
 *   <li>BPMN content parsing (ensuring that invalid BPMN XML is detected and valid BPMN XML is correctly processed).</li>
 * </ul>
 * </p>
 * <p>
 * References:
 * <ul>
 *   <li><a href="https://docs.oracle.com/en/java/javase/17/docs/specs/javadoc/doc-comment-spec.html">
 *       Oracle JavaDoc Guidelines</a></li>
 *   <li><a href="https://junit.org/junit5/docs/current/user-guide/">
 *       JUnit 5 User Guide</a></li>
 *   <li><a href="https://site.mockito.org/">
 *       Mockito Framework</a></li>
 * </ul>
 * </p>
 */
class TestDsfValidatorImpl {

    /**
     * A default instance of {@link DsfValidatorImpl} used for normal scenarios.
     */
    private final DsfValidatorImpl validator = new DsfValidatorImpl();

    /**
     * Nested class for testing file existence and readability.
     */
    @Nested
    @DisplayName("File existence and readability checks")
    class FileChecks {

        /**
         * Tests that when a BPMN file does not exist, the validator returns an
         * {@link UnparsableBpmnFileValidationItem}.
         *
         * @param tempDir a temporary directory provided by JUnit
         */
        @Test
        @DisplayName("Should return UnparsableBpmnFileValidationItem if file does not exist")
        void testFileDoesNotExist(@TempDir Path tempDir) {
            Path nonExistentFile = tempDir.resolve("nonexistent.bpmn");
            ValidationOutput output = validator.validate(nonExistentFile);
            List<AbstractValidationItem> issues = output.getValidationItems();

            assertFalse(Files.exists(nonExistentFile), "File should not exist.");
            assertEquals(1, issues.size(), "There should be exactly one issue.");
            assertTrue(issues.get(0) instanceof UnparsableBpmnFileValidationItem,
                    "Expected issue to be UnparsableBpmnFileValidationItem.");
        }

        /**
         * Tests that when a BPMN file exists but is not readable (mocked scenario),
         * the validator returns an {@link UnparsableBpmnFileValidationItem}.
         *
         * @param tempDir a temporary directory provided by JUnit
         * @throws IOException if an I/O error occurs during file operations
         */
        @Test
        @DisplayName("Should return UnparsableBpmnFileValidationItem if file is not readable (mocked)")
        void testFileNotReadable(@TempDir Path tempDir) throws IOException {
            // Create a normal file with some BPMN content.
            Path mockUnreadableFile = tempDir.resolve("unreadable.bpmn");
            Files.writeString(mockUnreadableFile, "Some BPMN content");

            // Create a spy to override the isFileReadable(...) method to force it to return false.
            DsfValidatorImpl spyValidator = Mockito.spy(new DsfValidatorImpl());
            Mockito.doReturn(false).when(spyValidator).isFileReadable(mockUnreadableFile);

            // Validate the file and check that an UnparsableBpmnFileValidationItem is returned.
            ValidationOutput output = spyValidator.validate(mockUnreadableFile);
            List<AbstractValidationItem> issues = output.getValidationItems();

            assertEquals(1, issues.size(), "There should be exactly one issue (UnparsableBpmnFileValidationItem).");
            assertTrue(issues.get(0) instanceof UnparsableBpmnFileValidationItem,
                    "Expected UnparsableBpmnFileValidationItem because the file is 'not readable'.");
        }
    }

    /**
     * Nested class for testing BPMN content parsing.
     */
    @Nested
    @DisplayName("Parsing BPMN content checks")
    class BpmnParsing {

        /**
         * Tests that an invalid BPMN XML file (non-BPMN content) results in an
         * {@link UnparsableBpmnFileValidationItem}.
         *
         * @param tempDir a temporary directory provided by JUnit
         * @throws IOException if an I/O error occurs during file operations
         */
        @Test
        @DisplayName("Should fail with UnparsableBpmnFileValidationItem if file is not valid BPMN XML")
        void testInvalidBpmn(@TempDir Path tempDir) throws IOException {
            Path invalidBpmn = tempDir.resolve("invalid.bpmn");
            try (BufferedWriter writer = Files.newBufferedWriter(invalidBpmn)) {
                writer.write("This is not BPMN XML at all");
            }

            ValidationOutput output = validator.validate(invalidBpmn);
            List<AbstractValidationItem> issues = output.getValidationItems();

            assertEquals(1, issues.size(), "There should be exactly one validation issue for invalid BPMN content.");
            assertTrue(issues.get(0) instanceof UnparsableBpmnFileValidationItem,
                    "Expected the issue to be an UnparsableBpmnFileValidationItem.");
        }

        /**
         * Tests that a minimal, valid BPMN file is parsed successfully and delegated
         * to the {@link BpmnModelValidator} without raising any validation issues.
         *
         * @param tempDir a temporary directory provided by JUnit
         * @throws IOException if an I/O error occurs during file operations
         */
        @Test
        @DisplayName("Should successfully parse BPMN file and delegate to BpmnModelValidator")
        void testValidBpmn(@TempDir Path tempDir) throws IOException {
            // Write a minimal BPMN file content.
            Path validBpmn = tempDir.resolve("valid.bpmn");
            String minimalBpmnContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                 targetNamespace="Examples">
                      <process id="TestProcess" isExecutable="true">
                        <startEvent id="StartEvent_1" name="Some name" />
                      </process>
                    </definitions>
                    """;
            Files.writeString(validBpmn, minimalBpmnContent);

            // Create a spy on BpmnModelValidator to ensure that it is called,
            // though in this test we focus on a successful parse with no validation issues.
            BpmnModelValidator mockValidator = Mockito.mock(BpmnModelValidator.class);
            when(mockValidator.validateModel(any(), any(), any())).thenReturn(List.of());

            // Validate the BPMN file and assert that no validation issues are reported.
            ValidationOutput output = validator.validate(validBpmn);
            List<AbstractValidationItem> issues = output.getValidationItems();

            assertTrue(issues.isEmpty(), "Expected no issues from minimal BPMN process.");
        }
    }
}
