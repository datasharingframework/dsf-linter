package dev.dsf.utils.validator;

import dev.dsf.utils.validator.logger.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for DsfValidatorImpl using a local multi-plugin test project.
 *
 * <p><b>Assumption:</b> This test suite assumes that the test project located at
 * {@code src/test/resources/dsf-multi-plugin-test} is compiled before these tests
 * are executed. This is typically handled by the {@code maven-invoker-plugin}
 * in the {@code validator-core/pom.xml}.</p>
 */
public class DsfMultiPluginValidatorTest {

    @TempDir
    private Path tempReportDir; // JUnit 5 provides a temporary directory for test outputs

    private Path projectPath;
    private DsfValidatorImpl validator;

    /**
     * A simple logger implementation for tests that prints to the console.
     */
    private static class ConsoleTestLogger implements Logger {
        @Override public void debug(String message) { System.out.println("[DEBUG] " + message); }
        @Override public void info(String message) { System.out.println("[INFO] " + message); }
        @Override public void warn(String message) { System.out.println("[WARN] " + message); }
        @Override public void error(String message) { System.err.println("[ERROR] " + message); }
        @Override public void error(String message, Throwable throwable) {
            System.err.println("[ERROR] " + message);
            throwable.printStackTrace(System.err);
        }
    }

    @BeforeEach
    void setUp() {
        // Set the system property to skip the 'clean' goal, avoiding file-locking issues on Windows.
        System.setProperty("dsf.validator.skipClean", "true");

        // Locate the test project reliably from test resources.
        URL projectUrl = getClass().getClassLoader().getResource("dsf-multi-plugin-test");
        assertNotNull(projectUrl, "Test project 'dsf-multi-plugin-test' not found in test resources.");
        projectPath = new File(projectUrl.getPath()).toPath();

        // Configure the validator.
        DsfValidatorImpl.Config config = new DsfValidatorImpl.Config(
                projectPath,
                tempReportDir,
                true,  // generateHtmlReport
                false, // failOnErrors (we want the test to complete and check the results).
                new ConsoleTestLogger()
        );

        validator = new DsfValidatorImpl(config);
    }

    @Test
    @DisplayName("Should fully validate the multi-plugin project, discover both plugins, and identify leftover files")
    void fullMultiPluginValidationTest() {
        // Arrange: Validator is configured in setUp. We expect the test project to be compiled.
        Path targetDir = projectPath.resolve("target/classes");
        assertTrue(Files.exists(targetDir),
                "The test project must be compiled. 'target/classes' not found. " +
                        "Ensure the maven-invoker-plugin is configured correctly in pom.xml.");

        // Act & Assert
        assertDoesNotThrow(() -> {
            DsfValidatorImpl.ValidationResult result = validator.validate();

            // 1. General Assertions
            assertNotNull(result, "Validation result should not be null.");
            assertTrue(result.executionTimeMs() > 0, "Execution time should be positive.");
            assertTrue(result.success(), "Validation success status should be true when failOnErrors is false.");

            // 2. Plugin Discovery Assertions
            Map<String, DsfValidatorImpl.PluginValidation> pluginValidations = result.pluginValidations();
            assertEquals(2, pluginValidations.size(),
                    "Exactly two plugins (v1 and v2) should have been discovered and validated.");

            // Check that both API versions were detected
            long v1Plugins = pluginValidations.values().stream()
                    .filter(p -> p.apiVersion() == dev.dsf.utils.validator.util.api.ApiVersion.V1).count();
            long v2Plugins = pluginValidations.values().stream()
                    .filter(p -> p.apiVersion() == dev.dsf.utils.validator.util.api.ApiVersion.V2).count();
            assertEquals(1, v1Plugins, "Exactly one v1 plugin should be detected.");
            assertEquals(1, v2Plugins, "Exactly one v2 plugin should be detected.");


            // 3. Leftover Resources Assertions
            assertNotNull(result.leftoverAnalysis(), "Leftover analysis result should not be null.");
            assertEquals(2, result.getLeftoverCount(),
                    "Exactly two leftover resources (one BPMN, one FHIR) should be detected.");

            assertTrue(result.leftoverAnalysis().leftoverBpmnPaths().stream()
                            .anyMatch(path -> path.endsWith("bpe/v2/send.bpmn")),
                    "send.bpmn should be listed as a leftover BPMN file.");

            assertTrue(result.leftoverAnalysis().leftoverFhirPaths().stream()
                            .anyMatch(path -> path.endsWith("fhir/ActivityDefinition/feasibilityExecute.xml")),
                    "feasibilityExecute.xml should be listed as a leftover FHIR file.");

            // 4. Report Generation Assertions
            assertNotNull(result.masterReportPath(), "Should have a master report path.");
            assertEquals(tempReportDir, result.masterReportPath());
            assertTrue(Files.exists(result.masterReportPath().resolve("report.html")), "Master HTML report should be generated.");
            assertTrue(Files.exists(result.masterReportPath().resolve("validation.json")), "Master JSON report should be generated.");

            // Check that each plugin has its own report directory
            for (DsfValidatorImpl.PluginValidation validation : pluginValidations.values()) {
                assertTrue(Files.exists(validation.reportPath()),
                        "Report directory should exist for plugin: " + validation.pluginName());
                assertTrue(Files.isDirectory(validation.reportPath()),
                        "Plugin report path should be a directory.");
            }

            System.out.println("Full validation test passed: 2 plugins validated, 2 leftover resources correctly identified.");

        }, "The validation process should complete without throwing an exception.");
    }
}