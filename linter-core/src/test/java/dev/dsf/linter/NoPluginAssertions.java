package dev.dsf.linter;

import dev.dsf.linter.output.LintingType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for projects without a ProcessPluginDefinition: linting does not abort, it reports exactly
 * one ERROR item of type {@link LintingType#PLUGIN_DEFINITION_NO_PLUGIN_FOUND}.
 */
final class NoPluginAssertions {

    private NoPluginAssertions() {
    }

    static void assertNoPluginReported(DsfLinter linter, String message) {
        DsfLinter.OverallLinterResult result = assertDoesNotThrow(linter::lint, message);
        assertEquals(1, result.getPluginErrors(), message);
        assertTrue(result.pluginLinter().values().stream()
                        .flatMap(plugin -> plugin.output().LintItems().stream())
                        .anyMatch(item -> item.getType() == LintingType.PLUGIN_DEFINITION_NO_PLUGIN_FOUND),
                message);
    }
}
