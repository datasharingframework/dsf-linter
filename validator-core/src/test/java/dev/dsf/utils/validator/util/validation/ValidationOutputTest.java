package dev.dsf.utils.validator.util.validation;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.logger.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ValidationOutputTest
{
    // Capture both System.out and System.err
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    // Matches: Found <n> <BPMN|FHIR> issue(s): (<e> errors, <w> warnings, <i> infos)
    private static final Pattern SUMMARY_PATTERN = Pattern.compile(
            "Found\\s+(\\d+)\\s+(?:BPMN|FHIR)\\s+issue\\(s\\):\\s*\\((\\d+)\\s+errors,\\s*(\\d+)\\s+warnings,\\s*(\\d+)\\s+infos\\)",
            Pattern.CASE_INSENSITIVE);

    /** Strips ANSI escape sequences (e.g., 24-bit color) from a console line. */
    private static String stripAnsi(String s)
    {
        return s == null ? null : s.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    private Logger mockLogger;

    @BeforeEach
    void setUp()
    {
        // Redirect both streams
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        mockLogger = mock(Logger.class);
        when(mockLogger.isVerbose()).thenReturn(false);
    }

    @AfterEach
    public void restoreStreams()
    {
        // Restore both streams
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String getCapturedOutput() {
        return outContent.toString() + errContent.toString();
    }
    

    @Test
    void testProjectWideSuccess_IsDisplayedInOwnSection()
    {
        List<AbstractValidationItem> items = List.of(
                new PluginDefinitionValidationItemSuccess(
                        new File("project"),
                        "project",
                        "All BPMN and FHIR resources are correctly referenced"
                )
        );

        ValidationOutput output = new ValidationOutput(items);
        output.printResults(mockLogger);

        String consoleOutput = getCapturedOutput();
        assertTrue(consoleOutput.contains("Project-Wide Validation"),
                "Should have 'Project-Wide Validation' section");
    }

    @Test
    void testPluginMetadataWarnings_DisplayedInOwnSection()
    {
        List<AbstractValidationItem> items = Arrays.asList(
                new PluginDefinitionNoProcessModelDefinedValidationItem(
                        new File("project"),
                        "com.example.PluginDefinition",
                        "This plugin does not define any BPMN process models"
                ),
                new PluginDefinitionNoFhirResourcesDefinedValidationItem(
                        new File("project"),
                        "com.example.PluginDefinition",
                        "This plugin does not define any FHIR resources"
                )
        );

        ValidationOutput output = new ValidationOutput(items);
        output.printResults(mockLogger);

        String consoleOutput = getCapturedOutput();
        assertTrue(consoleOutput.contains("Plugin Metadata Warnings"),
                "Should have 'Plugin Metadata Warnings' section");
    }

    @Test
    void testUnreferencedResources_DisplayedInDedicatedSection()
    {
        List<AbstractValidationItem> items = Arrays.asList(
                new PluginDefinitionProcessPluginRessourceNotLoadedValidationItem(
                        new File("old-process.bpmn"),
                        "bpe/v1/old-process.bpmn",
                        "BPMN file not referenced"
                ),
                new PluginDefinitionProcessPluginRessourceNotLoadedValidationItem(
                        new File("unused.xml"),
                        "fhir/Task/unused.xml",
                        "FHIR file not referenced"
                )
        );

        ValidationOutput output = new ValidationOutput(items);
        output.printResults(mockLogger);

        String consoleOutput = getCapturedOutput();
        assertTrue(consoleOutput.contains("Unreferenced Resources"),
                "Should have 'Unreferenced Resources' section");
        assertTrue(consoleOutput.contains("BPMN files"),
                "Should group BPMN leftovers");
        assertTrue(consoleOutput.contains("FHIR files"),
                "Should group FHIR leftovers");
    }

    @Test
    void testUnreferencedResources_GroupedByType()
    {
        List<AbstractValidationItem> items = Arrays.asList(
                new PluginDefinitionProcessPluginRessourceNotLoadedValidationItem(
                        new File("old1.bpmn"),
                        "bpe/v1/old1.bpmn",
                        "Not referenced"
                ),
                new PluginDefinitionProcessPluginRessourceNotLoadedValidationItem(
                        new File("old2.bpmn"),
                        "bpe/v2/old2.bpmn",
                        "Not referenced"
                ),
                new PluginDefinitionProcessPluginRessourceNotLoadedValidationItem(
                        new File("task1.xml"),
                        "fhir/Task/task1.xml",
                        "Not referenced"
                )
        );

        ValidationOutput output = new ValidationOutput(items);
        output.printResults(mockLogger);

        String consoleOutput = getCapturedOutput();
        assertTrue(consoleOutput.contains("BPMN files (2)"),
                "Should show BPMN count");
        assertTrue(consoleOutput.contains("FHIR files (1)"),
                "Should show FHIR count");
    }

    @Test
    void testRegularItems_StillGroupedBySeverity()
    {
        List<AbstractValidationItem> items = Arrays.asList(
                new TestBpmnElementValidationItem(ValidationSeverity.ERROR, "T1", "f.bpmn", "p", "E1"),
                new TestBpmnElementValidationItem(ValidationSeverity.ERROR, "T2", "f.bpmn", "p", "E2"),
                new TestBpmnElementValidationItem(ValidationSeverity.WARN, "T3", "f.bpmn", "p", "W1")
        );

        ValidationOutput output = new ValidationOutput(items);
        output.printResults(mockLogger);

        String consoleOutput = getCapturedOutput();

        // Simple 'contains' check is now sufficient because we capture the error stream
        assertTrue(consoleOutput.contains("ERROR items"),
                "Should have ERROR items header");
        assertTrue(consoleOutput.contains("WARN items"),
                "Should have WARN items header");
    }

    @Test
    void testAllItemsShown_NoLimiting()
    {
        List<AbstractValidationItem> items = new ArrayList<>();

        for (int i = 0; i < 50; i++)
        {
            items.add(new TestBpmnElementValidationItem(
                    ValidationSeverity.ERROR,
                    "Task_" + i,
                    "test.bpmn",
                    "process",
                    "Error " + i
            ));
        }

        ValidationOutput output = new ValidationOutput(items);
        output.printResults(mockLogger);

        // Instead of capturing logger arguments, we check the actual console output.
        // This is more consistent with the other tests in this class. The summary line
        // "Found 50 issue(s)" is sent to the logger, but the detailed breakdown
        // "ERROR items (50)" is printed to the console via System.err (using Console.red).
        // Verifying this console output is a reliable way to confirm all items were counted.
        String consoleOutput = getCapturedOutput();

        assertTrue(consoleOutput.contains("ERROR items (50)"),
                "All 50 error items should be counted and displayed in the ERROR section header");
    }

    @Test
    void testVerboseMode_ShowsAdditionalSuccessItems()
    {
        when(mockLogger.isVerbose()).thenReturn(true);

        List<AbstractValidationItem> items = Arrays.asList(
                new PluginDefinitionValidationItemSuccess(
                        new File("project"),
                        "project",
                        "All resources referenced"
                ),
                new BpmnElementValidationItemSuccess(
                        "StartEvent_1",
                        new File("test.bpmn"),
                        "process",
                        "Start event valid"
                )
        );

        ValidationOutput output = new ValidationOutput(items);
        output.printResults(mockLogger);

        String consoleOutput = getCapturedOutput();
        assertTrue(consoleOutput.contains("Additional SUCCESS items"),
                "Should show additional success items in verbose mode");
    }

    @Test
    void testNoIssues_ShowsSuccessMessage()
    {
        ValidationOutput output = ValidationOutput.empty();
        output.printResults(mockLogger);

        String consoleOutput = getCapturedOutput();
        assertTrue(consoleOutput.contains("File validated successfully, no issues were detected."),
                "Should show success message when no issues are present");
    }

    private static class TestBpmnElementValidationItem extends BpmnElementValidationItem
    {
        public TestBpmnElementValidationItem(
                ValidationSeverity severity,
                String elementId,
                String bpmnFileName,
                String processId,
                String description)
        {
            super(severity, elementId, bpmnFileName, processId, description);
        }

        @Override
        public String getDescription()
        {
            return description;
        }
    }
}