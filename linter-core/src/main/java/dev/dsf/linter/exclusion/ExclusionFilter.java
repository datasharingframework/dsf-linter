package dev.dsf.linter.exclusion;

import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.output.item.BpmnLintItem;
import dev.dsf.linter.output.item.FhirLintItem;
import dev.dsf.linter.output.item.PluginLintItem;

import java.util.List;
import java.util.Locale;

/**
 * Evaluates {@link ExclusionRule}s against lint items and partitions them into
 * included (visible in reports) and excluded (suppressed) sets.
 *
 * <h3>Matching logic</h3>
 * <ul>
 *   <li>Rules are OR-combined: an item is excluded if <em>any</em> rule matches it.</li>
 *   <li>Within a single rule, all non-null fields are AND-combined.</li>
 * </ul>
 */
public class ExclusionFilter {

    private final ExclusionConfig config;

    /**
     * Creates an ExclusionFilter backed by the given configuration.
     *
     * @param config the exclusion configuration (must not be {@code null})
     */
    public ExclusionFilter(ExclusionConfig config) {
        if (config == null) throw new IllegalArgumentException("ExclusionConfig must not be null");
        this.config = config;
    }

    /**
     * Returns the underlying configuration.
     */
    public ExclusionConfig getConfig() {
        return config;
    }

    /**
     * Returns {@code true} when at least one rule in the configuration matches {@code item}.
     *
     * @param item the lint item to test
     * @return {@code true} if the item should be suppressed
     */
    public boolean isExcluded(AbstractLintItem item) {
        if (item == null) return false;
        return config.getRules().stream()
                .filter(ExclusionRule::isValid)
                .anyMatch(rule -> matches(rule, item));
    }

    /**
     * Returns only the items that are NOT excluded.
     *
     * @param items the full list of lint items
     * @return items that pass through (i.e. are visible in reports)
     */
    public List<AbstractLintItem> filter(List<AbstractLintItem> items) {
        if (items == null || items.isEmpty()) return List.of();
        return items.stream()
                .filter(i -> !isExcluded(i))
                .toList();
    }

    /**
     * Returns only the items that ARE excluded by at least one rule.
     *
     * @param items the full list of lint items
     * @return items that were suppressed
     */
    public List<AbstractLintItem> getExcluded(List<AbstractLintItem> items) {
        if (items == null || items.isEmpty()) return List.of();
        return items.stream()
                .filter(this::isExcluded)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean matches(ExclusionRule rule, AbstractLintItem item) {
        if (hasValue(rule.getType())
                && !rule.getType().equalsIgnoreCase(item.getType().name())) {
            return false;
        }

        if (hasValue(rule.getSeverity())
                && !rule.getSeverity().equalsIgnoreCase(item.getSeverity().name())) {
            return false;
        }

        if (hasValue(rule.getFile())) {
            String fileHint = resolveFileHint(item);
            if (fileHint == null
                    || !fileHint.toLowerCase(Locale.ROOT)
                              .contains(rule.getFile().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        if (hasValue(rule.getMessageContains())) {
            String desc = item.getDescription();
            return desc != null
                    && desc.toLowerCase(Locale.ROOT)
                    .contains(rule.getMessageContains().toLowerCase(Locale.ROOT));
        }

        return true;
    }

    /**
     * Resolves the best available file-name hint for an item.
     * Uses specific subclass accessors before falling back to {@code toString()}.
     */
    private String resolveFileHint(AbstractLintItem item) {
        if (item instanceof BpmnLintItem b) return b.getBpmnFile();
        if (item instanceof FhirLintItem f) return f.getFhirFile();
        if (item instanceof PluginLintItem p) return p.getFileName();
        return null;
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}
