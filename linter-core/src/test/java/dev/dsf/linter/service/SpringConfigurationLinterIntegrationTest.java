package dev.dsf.linter.service;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration-style tests for {@link SpringConfigurationLinter}.
 *
 * <p>Verifies the core rule: every class referenced as a Camunda delegate or
 * listener in a BPMN file must be provided as a {@code @Bean} in at least one
 * of the {@code @Configuration} classes returned by
 * {@code ProcessPluginDefinition#getSpringConfigurations()}.</p>
 */
class SpringConfigurationLinterIntegrationTest {

    private Path tempProjectRoot;

    @BeforeEach
    void setUp() throws IOException {
        tempProjectRoot = Files.createTempDirectory("spring-config-integ-");
    }

    @AfterEach
    void tearDown() {
        deleteRecursively(tempProjectRoot.toFile());
    }

    @Test
    void missingRegistration_producesError() throws IOException {
        // BPMN references SampleDelegate; Object.class has no @Bean for it → ERROR.
        File bpmn = writeBpmn(tempProjectRoot, SampleDelegate.class.getName());

        PluginAdapter adapter = stubAdapter(List.of(Object.class));

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, List.of(bpmn), tempProjectRoot.toFile(), silentLogger());

        AbstractLintItem errorItem = items.stream()
                .filter(i -> i.getSeverity() == LinterSeverity.ERROR)
                .findFirst()
                .orElse(null);
        assertNotNull(errorItem, "Expected an ERROR when the BPMN-referenced class has no @Bean");
        assertEquals(LintingType.PLUGIN_DEFINITION_SPRING_CONFIGURATION_MISSING, errorItem.getType());
        assertTrue(errorItem.getDescription().contains(SampleDelegate.class.getSimpleName()),
                "Error description should mention the uncovered BPMN-referenced class");

        assertFalse(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.SUCCESS),
                "No SUCCESS item expected when at least one reference is uncovered");
    }

    @Test
    void registeredConfiguration_producesSuccess() throws IOException {
        File bpmn = writeBpmn(tempProjectRoot, SampleDelegate.class.getName());

        PluginAdapter adapter = stubAdapter(List.of(SampleAppConfig.class));

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, List.of(bpmn), tempProjectRoot.toFile(), silentLogger());

        assertFalse(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.ERROR),
                "Expected no ERROR when the required configuration is registered");
        assertTrue(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.SUCCESS),
                "Expected a SUCCESS item when all references resolve");
    }

    // ==================== Helpers ====================

    private static PluginAdapter stubAdapter(List<Class<?>> springConfigs) {
        return new PluginAdapter() {
            @Override public String getName() { return "sample-plugin"; }
            @Override public List<String> getProcessModels() { return Collections.emptyList(); }
            @Override public Map<String, List<String>> getFhirResourcesByProcessId() { return Collections.emptyMap(); }
            @Override public Class<?> sourceClass() { return SampleAppConfig.class; }
            @Override public String getResourceVersion() { return "1.0"; }
            @Override public List<Class<?>> getSpringConfigurations() { return springConfigs; }
        };
    }

    private static Logger silentLogger() {
        return new Logger() {
            @Override public void info(String message) {}
            @Override public void warn(String message) {}
            @Override public void error(String message) {}
            @Override public void error(String message, Throwable throwable) {}
            @Override public void debug(String message) {}
            @Override public boolean verbose() { return false; }
            @Override public boolean isVerbose() { return false; }
        };
    }

    private static File writeBpmn(Path dir, String camundaClass) throws IOException {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  targetNamespace="http://example.org">
                  <bpmn:process id="testorg_test" isExecutable="true">
                    <bpmn:serviceTask id="ST" name="select targets" camunda:class="%s"/>
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(camundaClass);
        Path p = dir.resolve("sample.bpmn");
        Files.writeString(p, bpmn, StandardCharsets.UTF_8);
        return p.toFile();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteRecursively(k);
        }
        f.delete();
    }
}
