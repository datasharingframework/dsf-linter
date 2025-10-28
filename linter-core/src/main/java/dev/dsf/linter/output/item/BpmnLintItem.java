package dev.dsf.linter.output.item;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dsf.linter.output.LinterSeverity;

/**
 * <p>
 * A base class for BPMN linting items. Instead of storing a {@code File},
 * we store a {@code String} <em>short filename</em>. This ensures JSON
 * serialization always displays only the short filename (e.g., "update-allow-list.bpmn").
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>
 *   // Suppose you have: File original = new File("C:\\path\\update-allow-list.bpmn");
 *   // This constructor sets bpmnFileName = "update-allow-list.bpmn"
 *   BpmnLintItem item = new SomeConcreteLintingItem(LinterSeverity.WARN, original.getName());
 * </pre>
 * </p>
 */
public abstract class BpmnLintItem extends AbstractLintItem {
    /**
     * The short BPMN filename (e.g., "update-allow-list.bpmn"), never the full path.
     */
    @JsonProperty("bpmnFile")  // JSON field name = "bpmnFile"
    protected final String bpmnFileName;

    /**
     * Constructs a {@code BpmnLintItem} with the given severity and a short
     * BPMN filename (no path).
     *
     * @param severity     the linting severity (WARN, ERROR, SUCCESS, etc.)
     * @param bpmnFileName the short BPMN filename (e.g., "update-allow-list.bpmn")
     */
    public BpmnLintItem(LinterSeverity severity, String bpmnFileName) {
        super(severity);
        this.bpmnFileName = (bpmnFileName != null) ? bpmnFileName : "unknown.bpmn";
    }

    /**
     * Returns the short BPMN file name, e.g., "update-allow-list.bpmn".
     */
    public String getBpmnFile() {
        return bpmnFileName;
    }

    @Override
    public String toString() {
        return getBpmnFile();
    }
}
