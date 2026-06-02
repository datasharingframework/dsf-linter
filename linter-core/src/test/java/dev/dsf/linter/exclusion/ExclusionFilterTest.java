package dev.dsf.linter.exclusion;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExclusionFilter}.
 *
 * <p>Tests cover: single-field matching, multi-field AND, multi-rule OR,
 * case-insensitivity, no-match cases, and null/empty safety.</p>
 */
class ExclusionFilterTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static ExclusionFilter filterWith(ExclusionRule... rules) {
        ExclusionConfig config = new ExclusionConfig(List.of(rules), false);
        return new ExclusionFilter(config);
    }

    private static ExclusionRule ruleFor(String type, String severity, String file, String messageContains) {
        return new ExclusionRule(type, severity, file, messageContains);
    }

    private static BpmnElementLintItem bpmnItem(LinterSeverity sev, LintingType type,
                                                 String bpmnFile, String description) {
        return new BpmnElementLintItem(sev, type, "element-1", bpmnFile, "process-1", description);
    }

    private static FhirElementLintItem fhirItem(LinterSeverity sev, LintingType type,
                                                 String resourceFile, String description) {
        return new FhirElementLintItem(sev, type, resourceFile, "http://example.com/ref", description);
    }

    private static PluginLintItem pluginItem(String fileName) {
        return new PluginLintItem(LinterSeverity.ERROR, LintingType.PLUGIN_DEFINITION_MISSING_SERVICE_LOADER_REGISTRATION, new File(fileName), "location", "Missing registration");
    }

    // -----------------------------------------------------------------------
    // 1. Type-only rule
    // -----------------------------------------------------------------------

    @Test
    void excludeByType_matchesExact() {
        ExclusionFilter filter = filterWith(
                ruleFor("BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING", null, null, null));

        BpmnElementLintItem item = bpmnItem(LinterSeverity.WARN,
                LintingType.BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING,
                "some.bpmn", "Missing historyTimeToLive");

        assertTrue(filter.isExcluded(item), "Should be excluded by type rule");
    }

    @Test
    void excludeByType_doesNotMatchOtherType() {
        ExclusionFilter filter = filterWith(
                ruleFor("BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING", null, null, null));

        BpmnElementLintItem item = bpmnItem(LinterSeverity.ERROR,
                LintingType.BPMN_PROCESS_ID_EMPTY,
                "some.bpmn", "Process ID is empty");

        assertFalse(filter.isExcluded(item), "Different type must not be excluded");
    }

    @Test
    void excludeByType_caseInsensitive() {
        ExclusionFilter filter = filterWith(
                ruleFor("bpmn_process_history_time_to_live_missing", null, null, null));

        BpmnElementLintItem item = bpmnItem(LinterSeverity.WARN,
                LintingType.BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING,
                "some.bpmn", "Missing historyTimeToLive");

        assertTrue(filter.isExcluded(item), "Type match should be case-insensitive");
    }

    // -----------------------------------------------------------------------
    // 2. Severity-only rule
    // -----------------------------------------------------------------------

    @Test
    void excludeBySeverity_matchesWarn() {
        ExclusionFilter filter = filterWith(ruleFor(null, "WARN", null, null));

        BpmnElementLintItem warnItem = bpmnItem(LinterSeverity.WARN,
                LintingType.BPMN_SERVICE_TASK_NAME_EMPTY, "x.bpmn", "msg");
        BpmnElementLintItem errorItem = bpmnItem(LinterSeverity.ERROR,
                LintingType.BPMN_SERVICE_TASK_NAME_EMPTY, "x.bpmn", "msg");

        assertTrue(filter.isExcluded(warnItem));
        assertFalse(filter.isExcluded(errorItem));
    }

    // -----------------------------------------------------------------------
    // 3. File-only rule (substring, case-insensitive)
    // -----------------------------------------------------------------------

    @Test
    void excludeByFile_substringMatchBpmn() {
        ExclusionFilter filter = filterWith(ruleFor(null, null, "update-allow-list", null));

        BpmnElementLintItem match = bpmnItem(LinterSeverity.WARN,
                LintingType.BPMN_SERVICE_TASK_NAME_EMPTY,
                "update-allow-list.bpmn", "msg");
        BpmnElementLintItem noMatch = bpmnItem(LinterSeverity.WARN,
                LintingType.BPMN_SERVICE_TASK_NAME_EMPTY,
                "other-process.bpmn", "msg");

        assertTrue(filter.isExcluded(match));
        assertFalse(filter.isExcluded(noMatch));
    }

    @Test
    void excludeByFile_substringMatchFhir() {
        ExclusionFilter filter = filterWith(ruleFor(null, null, "task-example", null));

        FhirElementLintItem match = fhirItem(LinterSeverity.ERROR,
                LintingType.FHIR_TASK_MISSING_INPUT, "task-example.xml", "msg");
        FhirElementLintItem noMatch = fhirItem(LinterSeverity.ERROR,
                LintingType.FHIR_TASK_MISSING_INPUT, "activity-definition.xml", "msg");

        assertTrue(filter.isExcluded(match));
        assertFalse(filter.isExcluded(noMatch));
    }

    @Test
    void excludeByFile_caseInsensitive() {
        ExclusionFilter filter = filterWith(ruleFor(null, null, "UPDATE-ALLOW-LIST", null));

        BpmnElementLintItem item = bpmnItem(LinterSeverity.WARN,
                LintingType.BPMN_SERVICE_TASK_NAME_EMPTY,
                "update-allow-list.bpmn", "msg");

        assertTrue(filter.isExcluded(item));
    }

    // -----------------------------------------------------------------------
    // 4. MessageContains-only rule
    // -----------------------------------------------------------------------

    @Test
    void excludeByMessage_substringMatch() {
        ExclusionFilter filter = filterWith(ruleFor(null, null, null, "optional field"));

        FhirElementLintItem match = fhirItem(LinterSeverity.WARN,
                LintingType.FHIR_VALUE_SET_MISSING_DESCRIPTION,
                "vs.xml", "This is an optional field that may be omitted");
        FhirElementLintItem noMatch = fhirItem(LinterSeverity.WARN,
                LintingType.FHIR_VALUE_SET_MISSING_DESCRIPTION,
                "vs.xml", "This field is required");

        assertTrue(filter.isExcluded(match));
        assertFalse(filter.isExcluded(noMatch));
    }

    @Test
    void excludeByMessage_caseInsensitive() {
        ExclusionFilter filter = filterWith(ruleFor(null, null, null, "OPTIONAL FIELD"));

        FhirElementLintItem item = fhirItem(LinterSeverity.WARN,
                LintingType.FHIR_VALUE_SET_MISSING_DESCRIPTION,
                "vs.xml", "This is an optional field");

        assertTrue(filter.isExcluded(item));
    }

    // -----------------------------------------------------------------------
    // 5. Multi-field AND within a single rule
    // -----------------------------------------------------------------------

    @Test
    void andCombination_allFieldsMustMatch() {
        ExclusionRule rule = ruleFor("BPMN_SERVICE_TASK_NAME_EMPTY", "WARN", "update", null);
        ExclusionFilter filter = filterWith(rule);

        // Matches type + severity + file
        BpmnElementLintItem fullMatch = bpmnItem(LinterSeverity.WARN,
                LintingType.BPMN_SERVICE_TASK_NAME_EMPTY, "update-allow-list.bpmn", "msg");

        // Matches type + severity but wrong file
        BpmnElementLintItem wrongFile = bpmnItem(LinterSeverity.WARN,
                LintingType.BPMN_SERVICE_TASK_NAME_EMPTY, "other.bpmn", "msg");

        // Matches type + file but wrong severity
        BpmnElementLintItem wrongSev = bpmnItem(LinterSeverity.ERROR,
                LintingType.BPMN_SERVICE_TASK_NAME_EMPTY, "update-allow-list.bpmn", "msg");

        assertTrue(filter.isExcluded(fullMatch));
        assertFalse(filter.isExcluded(wrongFile));
        assertFalse(filter.isExcluded(wrongSev));
    }

    // -----------------------------------------------------------------------
    // 6. Multi-rule OR combination
    // -----------------------------------------------------------------------

    @Test
    void orCombination_matchesEitherRule() {
        ExclusionFilter filter = filterWith(
                ruleFor("BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING", null, null, null),
                ruleFor(null, "WARN", "other.bpmn", null)
        );

        BpmnElementLintItem matchFirstRule = bpmnItem(LinterSeverity.ERROR,
                LintingType.BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING, "any.bpmn", "msg");

        BpmnElementLintItem matchSecondRule = bpmnItem(LinterSeverity.WARN,
                LintingType.BPMN_SERVICE_TASK_NAME_EMPTY, "other.bpmn", "msg");

        BpmnElementLintItem noMatch = bpmnItem(LinterSeverity.ERROR,
                LintingType.BPMN_SERVICE_TASK_NAME_EMPTY, "other.bpmn", "msg");

        assertTrue(filter.isExcluded(matchFirstRule));
        assertTrue(filter.isExcluded(matchSecondRule));
        assertFalse(filter.isExcluded(noMatch));
    }

    // -----------------------------------------------------------------------
    // 7. filter() and getExcluded() list operations
    // -----------------------------------------------------------------------

    @Test
    void filterList_removesMatchingItems() {
        ExclusionFilter filter = filterWith(ruleFor("BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING", null, null, null));

        BpmnElementLintItem excluded = bpmnItem(LinterSeverity.WARN,
                LintingType.BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING, "a.bpmn", "msg");
        BpmnElementLintItem included = bpmnItem(LinterSeverity.ERROR,
                LintingType.BPMN_PROCESS_ID_EMPTY, "a.bpmn", "msg");

        List<AbstractLintItem> items = List.of(excluded, included);
        List<AbstractLintItem> kept = filter.filter(items);
        List<AbstractLintItem> dropped = filter.getExcluded(items);

        assertEquals(1, kept.size());
        assertSame(included, kept.getFirst());
        assertEquals(1, dropped.size());
        assertSame(excluded, dropped.getFirst());
    }

    @Test
    void filterList_emptyInput_returnsEmpty() {
        ExclusionFilter filter = filterWith(ruleFor("BPMN_PROCESS_ID_EMPTY", null, null, null));
        assertTrue(filter.filter(List.of()).isEmpty());
        assertTrue(filter.getExcluded(List.of()).isEmpty());
    }

    // -----------------------------------------------------------------------
    // 8. No rules / null safety
    // -----------------------------------------------------------------------

    @Test
    void noRules_nothingExcluded() {
        ExclusionFilter filter = new ExclusionFilter(new ExclusionConfig(List.of(), false));

        BpmnElementLintItem item = bpmnItem(LinterSeverity.ERROR,
                LintingType.BPMN_PROCESS_ID_EMPTY, "a.bpmn", "msg");

        assertFalse(filter.isExcluded(item));
    }

    @Test
    void nullItemIsNotExcluded() {
        ExclusionFilter filter = filterWith(ruleFor("BPMN_PROCESS_ID_EMPTY", null, null, null));
        assertFalse(filter.isExcluded(null));
    }

    // -----------------------------------------------------------------------
    // 9. PluginLintItem file matching
    // -----------------------------------------------------------------------

    @Test
    void excludePluginItem_byFileSubstring() {
        ExclusionFilter filter = filterWith(ruleFor(null, null, "services", null));

        PluginLintItem match = pluginItem(
                "target/classes/META-INF/services/some.Service");

        PluginLintItem noMatch = pluginItem(
                "SomePlugin.class");

        assertTrue(filter.isExcluded(match));
        assertFalse(filter.isExcluded(noMatch));
    }

    // -----------------------------------------------------------------------
    // 10. affectsExitStatus is stored but does not change filter behavior
    // -----------------------------------------------------------------------

    @Test
    void affectsExitStatus_storedCorrectly() {
        ExclusionConfig withFlag = new ExclusionConfig(
                List.of(ruleFor("BPMN_PROCESS_ID_EMPTY", null, null, null)), true);
        assertTrue(withFlag.isAffectsExitStatus());

        ExclusionConfig withoutFlag = new ExclusionConfig(
                List.of(ruleFor("BPMN_PROCESS_ID_EMPTY", null, null, null)), false);
        assertFalse(withoutFlag.isAffectsExitStatus());
    }
}
