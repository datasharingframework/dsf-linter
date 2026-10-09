package dev.dsf.linter;

import dev.dsf.linter.input.InputResolver;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.LintingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "No plugin found" must be reported as an ERROR lint item with a regular report
 * (instead of aborting without any report), so that report consumers can show it.
 */
public class DsfNoPluginFoundTest {

    @TempDir
    Path tempDir;

    private static class TestLogger implements Logger {
        @Override public void debug(String message) { }
        @Override public boolean verbose() { return false; }
        @Override public boolean isVerbose() { return false; }
        @Override public void info(String message) { }
        @Override public void warn(String message) { }
        @Override public void error(String message) { System.err.println("[ERROR] " + message); }
        @Override public void error(String message, Throwable throwable) { System.err.println("[ERROR] " + message); }
    }

    private Path createJar(String name, String... entries) throws IOException {
        Path jar = tempDir.resolve(name);
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(out)) {
            for (int i = 0; i < entries.length; i += 2) {
                jos.putNextEntry(new JarEntry(entries[i]));
                jos.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
        return jar;
    }

    private DsfLinter.OverallLinterResult lint(Path jar, Path reportDir, boolean failOnErrors) throws Exception {
        TestLogger logger = new TestLogger();
        Optional<InputResolver.ResolutionResult> resolution = new InputResolver(logger).resolve(jar.toString());
        assertTrue(resolution.isPresent(), "JAR should be resolved");
        try {
            DsfLinter.Config config = new DsfLinter.Config(
                    resolution.get().resolvedPath(), reportDir, true, true, failOnErrors, logger);
            return new DsfLinter(config).lint();
        } finally {
            if (resolution.get().requiresCleanup()) {
                new InputResolver(logger).cleanup(resolution.get());
            }
        }
    }

    private void assertNoPluginError(DsfLinter.OverallLinterResult result, Path reportDir) throws IOException {
        assertEquals(1, result.pluginLinter().size(), "exactly one synthetic report entry expected");
        assertEquals(1, result.getPluginErrors());

        String name = result.pluginLinter().keySet().iterator().next();
        Path lintsJson = reportDir.resolve(name).resolve("lints.json");
        assertTrue(Files.exists(lintsJson), "lints.json must exist: " + lintsJson);
        assertTrue(Files.exists(reportDir.resolve(name).resolve("lints.html")), "lints.html must exist");
        assertTrue(Files.exists(reportDir.resolve("report.json")), "master report.json must exist");

        String json = Files.readString(lintsJson);
        assertTrue(json.contains(LintingType.PLUGIN_DEFINITION_NO_PLUGIN_FOUND.name()), json);
        assertTrue(json.contains("No ProcessPluginDefinition implementation found"), json);
        assertTrue(json.contains("dev.dsf.bpe.v2.ProcessPluginDefinition"), json);
    }

    @Test
    @DisplayName("JAR without service file and plugin class: ERROR report, exit status depends on failOnErrors")
    void jarWithoutPluginProducesErrorReport() throws Exception {
        // distinct JAR names: the extraction directory is derived from the name and may still be locked on Windows
        Path jar = createJar("no-plugin-nofail.jar", "readme.txt", "not a plugin");
        Path failJar = createJar("no-plugin-fail.jar", "readme.txt", "not a plugin");

        Path reportDir = tempDir.resolve("reports-nofail");
        DsfLinter.OverallLinterResult noFail = lint(jar, reportDir, false);
        assertNoPluginError(noFail, reportDir);
        assertTrue(noFail.success(), "--no-fail: result must be successful (exit code 0)");

        Path failDir = tempDir.resolve("reports-fail");
        DsfLinter.OverallLinterResult fail = lint(failJar, failDir, true);
        assertNoPluginError(fail, failDir);
        assertFalse(fail.success(), "failOnErrors: result must fail (exit code 1)");
    }

    @Test
    @DisplayName("Service file lists a class that is not a plugin: no crash, ERROR report incl. the ServiceLoader error")
    void brokenServiceRegistrationProducesErrorReport() throws Exception {
        // A provider in the unnamed module that does not implement the v2 interface makes ServiceLoader throw a
        // ServiceConfigurationError ("not a subtype"). (Classes of named modules, e.g. java.lang.Object, are ignored.)
        String providerClass = NoPluginAssertions.class.getName();
        byte[] classBytes;
        try (var in = NoPluginAssertions.class.getResourceAsStream("NoPluginAssertions.class")) {
            assertNotNull(in);
            classBytes = in.readAllBytes();
        }
        Path jar = tempDir.resolve("broken-service.jar");
        try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jos = new JarOutputStream(out)) {
            jos.putNextEntry(new JarEntry("META-INF/services/dev.dsf.bpe.v2.ProcessPluginDefinition"));
            jos.write((providerClass + "\n").getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
            jos.putNextEntry(new JarEntry(providerClass.replace('.', '/') + ".class"));
            jos.write(classBytes);
            jos.closeEntry();
        }

        Path reportDir = tempDir.resolve("reports-broken");
        DsfLinter.OverallLinterResult result = lint(jar, reportDir, false);
        assertNoPluginError(result, reportDir);
        assertTrue(result.success());

        String name = result.pluginLinter().keySet().iterator().next();
        String json = Files.readString(reportDir.resolve(name).resolve("lints.json"));
        assertTrue(json.contains("not a subtype"), "ServiceLoader error must be part of the report: " + json);
    }
}
