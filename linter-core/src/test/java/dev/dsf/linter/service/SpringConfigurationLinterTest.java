package dev.dsf.linter.service;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SpringConfigurationLinter}.
 *
 * <p>These tests exercise the decisions the linter makes from the
 * {@link PluginAdapter#getSpringConfigurations()} result and BPMN input.
 * The linter checks whether every BPMN-referenced class is provided as a
 * {@code @Bean} in one of the registered {@code @Configuration} classes.</p>
 */
class SpringConfigurationLinterTest {

    private Path tempProjectRoot;

    @BeforeEach
    void setUp() throws IOException {
        tempProjectRoot = Files.createTempDirectory("spring-config-linter-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempProjectRoot != null) {
            deleteRecursively(tempProjectRoot.toFile());
        }
    }

    @Test
    void emptySpringConfigurations_noBpmn_yieldsSuccess() {
        // Empty registered list + no BPMN references → nothing to check → SUCCESS.
        PluginAdapter adapter = stubAdapter(Collections.emptyList());

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, Collections.emptyList(), tempProjectRoot.toFile(), null);

        assertEquals(1, items.size(), "Expected exactly one lint item");
        assertEquals(LinterSeverity.SUCCESS, items.getFirst().getSeverity());
    }

    @Test
    void emptySpringConfigurations_withBpmn_yieldsError() throws IOException {
        // Empty registered list + BPMN reference → no @Bean can cover it → ERROR.
        File bpmn = writeBpmn(tempProjectRoot, "com.example.MyDelegate");
        PluginAdapter adapter = stubAdapter(Collections.emptyList());

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, List.of(bpmn), tempProjectRoot.toFile(), silentLogger());

        assertTrue(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.ERROR),
                "Expected ERROR when no registered config can cover a BPMN reference");
    }

    @Test
    void nonEmptyConfigurations_noBpmn_noProjectScan_yieldsSuccess() {
        PluginAdapter adapter = stubAdapter(List.of(DummyConfig.class));

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, Collections.emptyList(), tempProjectRoot.toFile(), null);

        assertEquals(1, items.size());
        assertEquals(LinterSeverity.SUCCESS, items.getFirst().getSeverity());
        assertEquals(LintingType.SUCCESS, items.getFirst().getType());
    }

    @Test
    void nullProjectDir_nonEmptyConfigurations_yieldsSuccess() {
        PluginAdapter adapter = stubAdapter(List.of(DummyConfig.class));

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, Collections.emptyList(), null, null);

        assertEquals(1, items.size());
        assertEquals(LinterSeverity.SUCCESS, items.getFirst().getSeverity());
    }

    @Test
    void bpmnReference_notCoveredByRegisteredBean_yieldsError() throws IOException {
        // DummyConfig has no @Bean methods at all, so the BPMN-referenced class
        // cannot be resolved → the linter must report an ERROR.
        File bpmn = writeBpmn(tempProjectRoot,
                "dev.dsf.bpe.service.SelectPingTargets");

        PluginAdapter adapter = stubAdapter(List.of(DummyConfig.class));

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, List.of(bpmn), tempProjectRoot.toFile(), silentLogger());

        assertTrue(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.ERROR),
                "Expected ERROR when a BPMN-referenced class has no matching @Bean");
        assertFalse(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.SUCCESS),
                "No SUCCESS item expected when at least one reference is uncovered");
    }

    @Test
    void bpmnReference_coveredByRegisteredBean_yieldsSuccess() throws IOException {
        // ConfigWithBean declares a @Bean for the exact BPMN-referenced class → SUCCESS.
        File bpmn = writeBpmn(tempProjectRoot,
                ConfigWithBean.BeanClass.class.getName());

        PluginAdapter adapter = stubAdapter(List.of(ConfigWithBean.class));

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, List.of(bpmn), tempProjectRoot.toFile(), silentLogger());

        assertFalse(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.ERROR),
                "No ERROR expected when the BPMN reference is covered by a registered @Bean");
        assertTrue(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.SUCCESS),
                "Expected SUCCESS when all BPMN references are covered");
    }


    // ==================== @Scope tests ====================

    @Test
    void beanWithoutScope_noMutableFields_yieldsWarn() throws IOException {
        // ConfigNoScope provides a @Bean with no @Scope; the bean class has only
        // final fields → WARN about missing scope, but no ERROR for mutable fields.
        File bpmn = writeBpmn(tempProjectRoot, ConfigNoScope.ImmutableBean.class.getName());
        PluginAdapter adapter = stubAdapter(List.of(ConfigNoScope.class));

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, List.of(bpmn), tempProjectRoot.toFile(), silentLogger());

        assertTrue(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.WARN
                        && i.getType() == LintingType.SPRING_BEAN_SCOPE_MISSING),
                "Expected WARN for missing @Scope");
        assertFalse(items.stream().anyMatch(i -> i.getType() == LintingType.SPRING_BEAN_SCOPE_MUTABLE_SINGLETON),
                "No ERROR expected when bean class has no mutable fields");
    }

    @Test
    void beanWithoutScope_mutableField_yieldsWarnAndError() throws IOException {
        // ConfigNoScopeMutable provides a @Bean with no @Scope; the bean class has a
        // mutable field → WARN for missing scope AND ERROR for mutable singleton.
        File bpmn = writeBpmn(tempProjectRoot, ConfigNoScopeMutable.MutableBean.class.getName());
        PluginAdapter adapter = stubAdapter(List.of(ConfigNoScopeMutable.class));

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, List.of(bpmn), tempProjectRoot.toFile(), silentLogger());

        assertTrue(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.WARN
                        && i.getType() == LintingType.SPRING_BEAN_SCOPE_MISSING),
                "Expected WARN for missing @Scope");
        assertTrue(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.ERROR
                        && i.getType() == LintingType.SPRING_BEAN_SCOPE_MUTABLE_SINGLETON),
                "Expected ERROR for mutable fields on effective-singleton bean");
    }

    @Test
    void beanWithExplicitSingleton_mutableField_yieldsWarnAndError() throws IOException {
        // ConfigExplicitSingleton uses @Scope("singleton") explicitly; the bean class
        // has a mutable field → WARN for explicit singleton AND ERROR for mutable fields.
        File bpmn = writeBpmn(tempProjectRoot, ConfigExplicitSingleton.MutableBean.class.getName());
        PluginAdapter adapter = stubAdapter(List.of(ConfigExplicitSingleton.class));

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, List.of(bpmn), tempProjectRoot.toFile(), silentLogger());

        assertTrue(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.WARN
                        && i.getType() == LintingType.SPRING_BEAN_SCOPE_SINGLETON_EXPLICIT),
                "Expected WARN for explicit singleton scope");
        assertTrue(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.ERROR
                        && i.getType() == LintingType.SPRING_BEAN_SCOPE_MUTABLE_SINGLETON),
                "Expected ERROR for mutable fields on explicit-singleton bean");
    }

    @Test
    void beanWithPrototypeScope_yieldsSuccess_evenWithMutableFields() throws IOException {
        // ConfigPrototype uses @Scope("prototype"); the bean class has a mutable field
        // but prototype scope is safe → SUCCESS, no ERROR for mutable fields.
        File bpmn = writeBpmn(tempProjectRoot, ConfigPrototype.AnyBean.class.getName());
        PluginAdapter adapter = stubAdapter(List.of(ConfigPrototype.class));

        List<AbstractLintItem> items = SpringConfigurationLinter.lint(
                adapter, List.of(bpmn), tempProjectRoot.toFile(), silentLogger());

        assertTrue(items.stream().anyMatch(i -> i.getSeverity() == LinterSeverity.SUCCESS
                        && i.getType() == LintingType.SPRING_BEAN_SCOPE_PROTOTYPE),
                "Expected SUCCESS for prototype-scoped bean");
        assertFalse(items.stream().anyMatch(i -> i.getType() == LintingType.SPRING_BEAN_SCOPE_MUTABLE_SINGLETON),
                "No ERROR expected for prototype-scoped bean regardless of mutable fields");
    }

    // ==================== Helpers ====================

    private static PluginAdapter stubAdapter(List<Class<?>> springConfigs) {
        return new PluginAdapter() {
            @Override public String getName() { return "test-plugin"; }
            @Override public List<String> getProcessModels() { return Collections.emptyList(); }
            @Override public Map<String, List<String>> getFhirResourcesByProcessId() { return Collections.emptyMap(); }
            @Override public Class<?> sourceClass() { return DummyConfig.class; }
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

    /** Placeholder with no @Bean methods – any BPMN reference will be uncovered. */
    static final class DummyConfig {
    }

    /** Config that provides exactly one @Bean for {@link BeanClass}. */
    static final class ConfigWithBean {
        static final class BeanClass {
        }

        @Bean
        public BeanClass beanClass() {
            return new BeanClass();
        }
    }

    /** Config with a @Bean and no @Scope; bean class has only final fields (immutable). */
    static final class ConfigNoScope {
        @SuppressWarnings("unused")
        static final class ImmutableBean {
            private final int x = 0;
        }

        @Bean
        public ImmutableBean bean() {
            return new ImmutableBean();
        }
    }

    /** Config with a @Bean and no @Scope; bean class has a mutable instance field. */
    static final class ConfigNoScopeMutable {
        @SuppressWarnings("unused")
        static final class MutableBean {
            private int counter; // mutable – not static, not final
        }

        @Bean
        public MutableBean bean() {
            return new MutableBean();
        }
    }

    /** Config with an explicit @Scope("singleton"); bean class has a mutable field. */
    static final class ConfigExplicitSingleton {
        @SuppressWarnings("unused")
        static final class MutableBean {
            private String state; // mutable
        }

        @Bean
        @Scope("singleton")
        public MutableBean bean() {
            return new MutableBean();
        }
    }

    /** Config with @Scope("prototype"); bean class has a mutable field (safe for prototype). */
    static final class ConfigPrototype {
        @SuppressWarnings("unused")
        static final class AnyBean {
            private int x; // mutable, but prototype-scoped → no ERROR
        }

        @Bean
        @Scope("prototype")
        public AnyBean bean() {
            return new AnyBean();
        }
    }
}
