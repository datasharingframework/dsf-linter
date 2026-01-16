package dev.dsf.linter.output.item;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

/**
 * Base class for BPMN linting items.
 * <p>
 * Instead of storing a {@code File}, we store a {@code String} short filename.
 * This ensures JSON serialization always displays only the short filename
 * (e.g., "update-allow-list.bpmn").
 * </p>
 */
public abstract class BpmnLintItem extends AbstractLintItem {
    /**
     * The short BPMN filename (e.g., "update-allow-list.bpmn"), never the full path.
     */
    @JsonProperty("bpmnFile")
    protected final String bpmnFileName;

    /**
     * Constructs a {@code BpmnLintItem} with the given severity, type, and filename.
     *
     * @param severity     the linting severity (WARN, ERROR, SUCCESS, etc.)
     * @param type         the linting type/category
     * @param bpmnFileName the short BPMN filename (e.g., "update-allow-list.bpmn")
     */
    public BpmnLintItem(LinterSeverity severity, LintingType type, String bpmnFileName) {
        super(severity, type);
        this.bpmnFileName = (bpmnFileName != null) ? bpmnFileName : "unknown.bpmn";
    }

    /**
     * Constructs a {@code BpmnLintItem} with severity and filename (backward compatible).
     *
     * @param severity     the linting severity
     * @param bpmnFileName the short BPMN filename
     * @deprecated Use {@link #BpmnLintItem(LinterSeverity, LintingType, String)} instead
     */
    @Deprecated
    public BpmnLintItem(LinterSeverity severity, String bpmnFileName) {
        this(severity, LintingType.UNKNOWN, bpmnFileName);
    }

    /**
     * Returns the short BPMN file name, e.g., "update-allow-list.bpmn".
     *
     * @return the BPMN filename
     */
    public String getBpmnFile() {
        return bpmnFileName;
    }

    @Override
    public String toString() {
        return getBpmnFile();
    }
}
