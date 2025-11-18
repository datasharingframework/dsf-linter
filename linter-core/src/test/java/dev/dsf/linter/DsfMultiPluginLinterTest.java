package dev.dsf.linter;

import dev.dsf.linter.input.InputResolver;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.logger.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for DsfLinter using a pre-built multi-plugin test JAR.
 *
 * <p><b>Assumption:</b> This test suite assumes that the JAR file located at
 * {@code src/test/resources/dsf-multi-plugin-test/dsf-multi-plugin-test-1.0-SNAPSHOT.jar}
 * exists and contains the compiled multi-plugin test project.</p>
 *
 * <p>The linter now only accepts JAR files as input, not Maven project directories.</p>
 */
public class DsfMultiPluginLinterTest {

    @TempDir
    private Path tempReportDir;

    private DsfLinter linter;
    private InputResolver.ResolutionResult resolutionResult;

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
    void setUp() throws Exception {
        ConsoleTestLogger logger = new ConsoleTestLogger();
        
        // Locate the pre-built JAR file from test resources
        URL jarUrl = getClass().getClassLoader().getResource("dsf-multi-plugin-test/dsf-multi-plugin-test-1.0-SNAPSHOT.jar");
        assertNotNull(jarUrl, "Test JAR 'dsf-multi-plugin-test-1.0-SNAPSHOT.jar' not found in test resources.");
        
        Path jarPath = new File(jarUrl.toURI()).toPath();
        assertTrue(Files.exists(jarPath), "JAR file should exist at: " + jarPath);

        // Use InputResolver to extract the JAR file
        InputResolver inputResolver = new InputResolver(logger);
        Optional<InputResolver.ResolutionResult> resolutionOpt = inputResolver.resolve(jarPath.toString());
        assertTrue(resolutionOpt.isPresent(), "JAR file should be resolved successfully");
        
        resolutionResult = resolutionOpt.get();
        Path extractedPath = resolutionResult.resolvedPath();
        assertTrue(Files.exists(extractedPath), "Extracted directory should exist");

        // Configure the linter with the extracted JAR directory
        DsfLinter.Config config = new DsfLinter.Config(
                extractedPath,
                tempReportDir,
                true,
                true,
                false,
                logger
        );

        linter = new DsfLinter(config);
    }

    @AfterEach
    void tearDown() {
        // Clean up temporary extraction directory if needed
        if (resolutionResult != null && resolutionResult.requiresCleanup()) {
            InputResolver resolver = new InputResolver(new ConsoleTestLogger());
            resolver.cleanup(resolutionResult);
        }
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
                    "Exactly two leftover BPMN resources should be detected.");

            // Verify we have leftover BPMN files
            assertEquals(2, result.leftoverAnalysis().leftoverBpmnPaths().size(),
                    "Should have exactly 2 leftover BPMN files.");
            
            // Verify specific leftover BPMN files
            assertTrue(result.leftoverAnalysis().leftoverBpmnPaths().stream()
                            .anyMatch(path -> path.endsWith("send.bpmn")),
                    "send.bpmn should be listed as a leftover BPMN file.");
            
            assertTrue(result.leftoverAnalysis().leftoverBpmnPaths().stream()
                            .anyMatch(path -> path.endsWith("download-allow-list.bpmn")),
                    "download-allow-list.bpmn should be listed as a leftover BPMN file.");

            // Verify no leftover FHIR files (all FHIR files are referenced)
            assertEquals(0, result.leftoverAnalysis().leftoverFhirPaths().size(),
                    "Should have no leftover FHIR files (all FHIR files are referenced).");

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