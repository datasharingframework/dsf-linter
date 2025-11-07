package dev.dsf.linter;

import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.logger.Logger;
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
 * Integration test for DsfLinter using a local multi-plugin test project.
 *
 * <p><b>Assumption:</b> This test suite assumes that the test project located at
 * {@code src/test/resources/dsf-multi-plugin-test} is compiled before these tests
 * are executed. This is typically handled by the {@code maven-invoker-plugin}
 * in the {@code linter-core/pom.xml}.</p>
 */
public class DsfMultiPluginLinterTest {

    @TempDir
    private Path tempReportDir;

    private DsfLinter linter;

    /**
     * A simple logger implementation for tests that prints to the console.
     */
    private static class ConsoleTestLogger implements Logger {
        @Override public void debug(String message) { System.out.println("[DEBUG] " + message); }

        @Override
        public boolean verbose() {
            return false;
        }

        @Override
        public boolean isVerbose() {
            return false;
        }

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
        // Locate the test project reliably from test resources.
        URL projectUrl = getClass().getClassLoader().getResource("dsf-multi-plugin-test");
        assertNotNull(projectUrl, "Test project 'dsf-multi-plugin-test' not found in test resources.");
        Path projectPath = new File(projectUrl.getPath()).toPath();

        // Configure the linter.
        DsfLinter.Config config = new DsfLinter.Config(
                projectPath,
                tempReportDir,
                true,
                true,
                false,
                new String[0],
                null,
                new ConsoleTestLogger()
        );

        linter = new DsfLinter(config);
    }

    @Test
    @DisplayName("Should fully lint the multi-plugin project, discover both plugins, and identify leftover files")
    void fullMultiPluginLintingTest() {

        // Act & Assert
        assertDoesNotThrow(() -> {
            DsfLinter.OverallLinterResult result = linter.lint();

            // 1. General Assertions
            assertNotNull(result, "Linting result should not be null.");
            assertTrue(result.executionTimeMs() > 0, "Execution time should be positive.");
            assertTrue(result.success(), "Linting success status should be true when failOnErrors is false.");

            // 2. Plugin Discovery Assertions
            Map<String, DsfLinter.PluginLinter> pluginLints = result.pluginLinter();
            assertEquals(2, pluginLints.size(),
                    "Exactly two plugins should have been discovered and linted.");

            // Check that both API versions were detected
            long v1Plugins = pluginLints.values().stream()
                    .filter(p -> p.apiVersion() == ApiVersion.V1).count();
            assertEquals(2, v1Plugins, "Exactly two v1 plugins should be detected.");


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
            assertTrue(Files.exists(result.masterReportPath().resolve("report.html")),
                    "Master HTML report should be generated.");

            // Check that each plugin has its own report directory
            for (DsfLinter.PluginLinter lint : pluginLints.values()) {
                assertTrue(Files.exists(lint.reportPath()),
                        "Report directory should exist for plugin: " + lint.pluginName());
                assertTrue(Files.isDirectory(lint.reportPath()),
                        "Plugin report path should be a directory.");
            }

            System.out.println("Full linting test passed: 2 plugins linted, 2 leftover resources correctly identified.");

        }, "The linting process should complete without throwing an exception.");
    }
}