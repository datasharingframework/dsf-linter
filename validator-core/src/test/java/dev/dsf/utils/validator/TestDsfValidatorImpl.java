package dev.dsf.utils.validator;

import dev.dsf.utils.validator.item.AbstractValidationItem;
import dev.dsf.utils.validator.item.UnparsableBpmnFileValidationItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <p>
 * This class tests {@link DsfValidatorImpl} BPMN validation by creating
 * temporary directory structures under {@code tempDir} (provided by JUnit).
 * </p>
 *
 * <p>
 * The tests are organized into nested classes:
 * <ul>
 *   <li>{@code FileChecks} - Tests for file existence and readability under the BPMN validation logic</li>
 *   <li>{@code BpmnParsing} - Tests to confirm correct BPMN content parsing</li>
 * </ul>
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 *   <li><a href="https://junit.org/junit5/docs/current/user-guide/">JUnit 5 User Guide</a></li>
 *   <li><a href="https://site.mockito.org/">Mockito Documentation</a></li>
 *   <li><a href="https://docs.oracle.com/javase/8/docs/api/java/nio/file/Files.html">Java NIO Files API</a></li>
 *   <li>BPMN 2.0: <a href="https://www.omg.org/spec/BPMN/2.0">https://www.omg.org/spec/BPMN/2.0</a></li>
 * </ul>
 */
class TestDsfValidatorImpl
{
    /**
     * A default {@link DsfValidatorImpl} instance used in the test cases.
     */
    private final DsfValidatorImpl validator = new DsfValidatorImpl();

    @Nested
    @DisplayName("File existence and readability checks")
    class FileChecks
    {
        /**
         * Tests behavior when a discovered BPMN file is unreadable at the OS level.
         * We expect an {@link UnparsableBpmnFileValidationItem}.
         *
         * @param tempDir a temporary directory provided by JUnit
         * @throws IOException if any I/O error occurs while creating/writing the file
         */
        @Test
        @DisplayName("Should return UnparsableBpmnFileValidationItem if a discovered BPMN file is unreadable")
        void testUnreadableBpmnFile(@TempDir Path tempDir) throws IOException
        {
            // Create the directory structure: <tempDir>/src/main/resources/bpe
            Path bpmnDir = tempDir.resolve("src").resolve("main").resolve("resources").resolve("bpe");
            Files.createDirectories(bpmnDir);

            // Create a BPMN file
            Path unreadableBpmn = bpmnDir.resolve("unreadable.bpmn");
            Files.writeString(unreadableBpmn, "Test content");

            // Attempt to remove read permissions (may fail on some systems)
            File fileObj = unreadableBpmn.toFile();
            boolean removedReadPermission = fileObj.setReadable(false, false);

            // Validate the entire directory for BPMN files
            ValidationOutput output = validator.validateAllBpmnFilesForTest(tempDir.toFile());
            List<AbstractValidationItem> issues = output.validationItems();

            if (removedReadPermission)
            {
                // If permissions were successfully revoked, we expect exactly one issue
                assertEquals(1, issues.size(),
                        "Expected exactly 1 validation item when the file is genuinely unreadable.");
                assertInstanceOf(UnparsableBpmnFileValidationItem.class, issues.getFirst(), "Expected an UnparsableBpmnFileValidationItem for an unreadable BPMN file.");
            }
            else
            {
                // If permissions could not be changed, the test may yield zero issues
                System.out.println("WARNING: Could not remove read permissions; "
                        + "the test may yield different results on this system.");
            }
        }

        /**
         * Tests behavior when the BPMN directory (src/main/resources/bpe) does not exist.
         * The validator returns an empty {@link ValidationOutput}, as no BPMN files are discovered.
         */
        @Test
        @DisplayName("Should return empty list if src/main/resources/bpe directory does not exist")
        void testNoBpmnDirectory(@TempDir Path tempDir)
        {
            // Do NOT create the bpe directory
            // => validator.validateAllBpmnFilesForTest(tempDir) will not find any BPMN files
            ValidationOutput output = validator.validateAllBpmnFilesForTest(tempDir.toFile());
            List<AbstractValidationItem> issues = output.validationItems();

            // We expect an empty list because the BPMN directory does not exist
            assertTrue(issues.isEmpty(),
                    "Expected an empty list if the BPMN directory is missing or empty.");
        }
    }

    @Nested
    @DisplayName("Parsing BPMN content checks")
    class BpmnParsing
    {
        /**
         * Writes invalid BPMN content to a file and checks if an
         * {@link UnparsableBpmnFileValidationItem} is raised.
         *
         * @param tempDir a temporary directory provided by JUnit
         * @throws IOException if any I/O error occurs while writing the file
         */
        @Test
        @DisplayName("Should fail with UnparsableBpmnFileValidationItem if file contains invalid BPMN XML")
        void testInvalidBpmn(@TempDir Path tempDir) throws IOException
        {
            // Create the directory structure
            Path bpmnDir = tempDir.resolve("src").resolve("main").resolve("resources").resolve("bpe");
            Files.createDirectories(bpmnDir);

            // Create an invalid BPMN file
            Path invalidBpmn = bpmnDir.resolve("invalid.bpmn");
            try (BufferedWriter writer = Files.newBufferedWriter(invalidBpmn))
            {
                writer.write("Not valid BPMN XML content");
            }

            // Validate
            ValidationOutput output = validator.validateAllBpmnFilesForTest(tempDir.toFile());
            List<AbstractValidationItem> issues = output.validationItems();

            assertEquals(1, issues.size(),
                    "Expected exactly one validation item for invalid BPMN content.");
            assertInstanceOf(UnparsableBpmnFileValidationItem.class, issues.get(0),
                    "Expected an UnparsableBpmnFileValidationItem for invalid BPMN content.");
        }

        /**
         * Tests whether a minimal valid BPMN file is parsed without errors.
         * Since the {@code BpmnModelValidator} might not flag any issues for a minimal definition,
         * we expect no validation items (or only {@code SUCCESS} items).
         *
         * @param tempDir a temporary directory provided by JUnit
         * @throws IOException if any I/O error occurs while writing the file
         */
        @Test
        @DisplayName("Should parse minimal valid BPMN without errors")
        void testValidBpmn(@TempDir Path tempDir) throws IOException
        {
            // Create the directory structure
            Path bpmnDir = tempDir.resolve("src").resolve("main").resolve("resources").resolve("bpe");
            Files.createDirectories(bpmnDir);

            // Create a minimal valid BPMN file
            String minimalBpmnContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             targetNamespace="Examples">
                  <process id="TestProcess" isExecutable="true">
                    <startEvent id="StartEvent_1" name="Sample Start Event" />
                  </process>
                </definitions>
                """;
            Path validBpmn = bpmnDir.resolve("valid.bpmn");
            Files.writeString(validBpmn, minimalBpmnContent);

            // Validate
            ValidationOutput output = validator.validateAllBpmnFilesForTest(tempDir.toFile());
            List<AbstractValidationItem> issues = output.validationItems();

            // Check if there are any ERROR, WARN, or INFO items
            boolean hasUndesiredIssues = issues.stream().anyMatch(item ->
                    item.getSeverity() == ValidationSeverity.ERROR ||
                            item.getSeverity() == ValidationSeverity.WARN  ||
                            item.getSeverity() == ValidationSeverity.INFO
            );

            assertFalse(hasUndesiredIssues,
                    "Expected no ERROR/WARN/INFO-level items for a minimal valid BPMN file.");

            // Optionally verify that any existing items are only SUCCESS
            boolean allSuccess = issues.stream()
                    .allMatch(item -> item.getSeverity() == ValidationSeverity.SUCCESS);
            assertTrue(allSuccess, "Expected only SUCCESS-level items, no errors or warnings.");

            // Debug print
            issues.forEach(item -> System.out.println("Issue: " + item));
        }
    }
}
