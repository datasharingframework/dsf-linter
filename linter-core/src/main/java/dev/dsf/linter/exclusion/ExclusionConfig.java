package dev.dsf.linter.exclusion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Top-level exclusion configuration loaded from {@code dsf-linter-exclusions.json}.
 * <p>
 * Contains an ordered list of {@link ExclusionRule}s and an optional flag that controls
 * whether excluded items still contribute to the exit status.
 * </p>
 *
 * <h3>Sample {@code dsf-linter-exclusions.json}</h3>
 * <pre>{@code
 * {
 *   "affectsExitStatus": false,
 *   "rules": [
 *     { "type": "BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING" },
 *     { "severity": "WARN", "file": "update-allow-list.bpmn" },
 *     { "messageContains": "optional field" }
 *   ]
 * }
 * }</pre>
 *
 * @see ExclusionRule
 * @see ExclusionFilter
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExclusionConfig {

    /**
     * When {@code false} (default), excluded items are also removed from exit-status
     * error/warning counts — they are fully suppressed.
     * When {@code true}, excluded items still count toward the exit status (they are only
     * hidden from the generated reports).
     */
    private boolean affectsExitStatus = false;

    private List<ExclusionRule> rules = new ArrayList<>();

    public ExclusionConfig() {
    }

    public ExclusionConfig(List<ExclusionRule> rules, boolean affectsExitStatus) {
        this.rules = new ArrayList<>(rules);
        this.affectsExitStatus = affectsExitStatus;
    }

    public boolean isAffectsExitStatus() {
        return affectsExitStatus;
    }

    public void setAffectsExitStatus(boolean affectsExitStatus) {
        this.affectsExitStatus = affectsExitStatus;
    }

    public List<ExclusionRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public void setRules(List<ExclusionRule> rules) {
        this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
    }

    /** Returns {@code true} when there is at least one valid rule to evaluate. */
    public boolean hasRules() {
        return rules != null && rules.stream().anyMatch(ExclusionRule::isValid);
    }

    @Override
    public String toString() {
        return "ExclusionConfig{rules=" + rules.size() + ", affectsExitStatus=" + affectsExitStatus + "}";
    }
}
