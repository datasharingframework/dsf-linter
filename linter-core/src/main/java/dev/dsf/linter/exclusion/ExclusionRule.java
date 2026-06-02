package dev.dsf.linter.exclusion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single exclusion rule that describes which lint items to suppress from reports.
 * <p>
 * All non-null fields are AND-combined: an item is excluded only when every specified
 * field matches. Multiple rules in an {@link ExclusionConfig} are OR-combined.
 * </p>
 *
 * <h3>Matching semantics</h3>
 * <ul>
 *   <li>{@code type} — exact, case-insensitive match against {@link dev.dsf.linter.output.LintingType#name()}</li>
 *   <li>{@code severity} — exact, case-insensitive match against {@link dev.dsf.linter.output.LinterSeverity#name()}</li>
 *   <li>{@code file} — case-insensitive substring match against the item's file name</li>
 *   <li>{@code messageContains} — case-insensitive substring match against the item's description</li>
 * </ul>
 *
 * <h3>Example (JSON)</h3>
 * <pre>
 * { "type": "BPMN_PROCESS_HISTORY_TIME_TO_LIVE_MISSING" }
 * { "severity": "WARN", "file": "update-allow-list.bpmn" }
 * { "messageContains": "optional field" }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExclusionRule {

    private String type;
    private String severity;
    private String file;
    private String messageContains;

    public ExclusionRule() {
    }

    public ExclusionRule(String type, String severity, String file, String messageContains) {
        this.type = type;
        this.severity = severity;
        this.file = file;
        this.messageContains = messageContains;
    }

    /**
     * Returns the exact {@link dev.dsf.linter.output.LintingType} name to match,
     * or {@code null} to match any type.
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the exact {@link dev.dsf.linter.output.LinterSeverity} name to match,
     * or {@code null} to match any severity.
     */
    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    /**
     * Returns the file-name substring to match (case-insensitive),
     * or {@code null} to match any file.
     */
    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    /**
     * Returns the description substring to match (case-insensitive),
     * or {@code null} to match any description.
     */
    public String getMessageContains() {
        return messageContains;
    }

    public void setMessageContains(String messageContains) {
        this.messageContains = messageContains;
    }

    /**
     * Returns {@code true} when this rule has at least one non-null, non-blank criterion.
     * An empty rule would match everything and is rejected by the loader.
     */
    public boolean isValid() {
        return hasValue(type) || hasValue(severity) || hasValue(file) || hasValue(messageContains);
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ExclusionRule{");
        if (hasValue(type))            sb.append("type='").append(type).append("', ");
        if (hasValue(severity))        sb.append("severity='").append(severity).append("', ");
        if (hasValue(file))            sb.append("file='").append(file).append("', ");
        if (hasValue(messageContains)) sb.append("messageContains='").append(messageContains).append("', ");
        if (sb.charAt(sb.length() - 2) == ',') sb.setLength(sb.length() - 2);
        sb.append("}");
        return sb.toString();
    }
}
