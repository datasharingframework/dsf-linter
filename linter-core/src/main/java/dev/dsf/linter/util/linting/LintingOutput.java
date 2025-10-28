package dev.dsf.linter.util.linting;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.output.item.BpmnElementLintItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record LintingOutput(List<AbstractLintItem> LintItems)
{
    public static final Map<LinterSeverity, Integer> SEVERITY_RANK = Map.of(
            LinterSeverity.ERROR,   0,
            LinterSeverity.WARN,    1,
            LinterSeverity.INFO,    2,
            LinterSeverity.SUCCESS, 3
    );

    public LintingOutput(List<AbstractLintItem> LintItems)
    {
        this.LintItems = new ArrayList<>(LintItems);
        this.LintItems.sort(
                Comparator.comparingInt((AbstractLintItem i) ->
                                SEVERITY_RANK.getOrDefault(i.getSeverity(), Integer.MAX_VALUE))
                        .thenComparing(AbstractLintItem::toString)
        );
    }

    /**
     * Returns the sorted list of lint items.
     *
     * @return a sorted, unmodifiable view of the lint items list.
     */
    public List<AbstractLintItem> LintItems()
    {
        return Collections.unmodifiableList(LintItems);
    }

    /**
     * Returns the count of lint items with ERROR severity.
     * <p>
     * This method filters the lint items and counts only those with
     * {@link LinterSeverity#ERROR} severity level.
     * </p>
     *
     * @return the number of error items in the linting output
     */
    public int getErrorCount() {
        return (int) LintItems.stream()
                .filter(item -> item.getSeverity() == LinterSeverity.ERROR)
                .count();
    }

    /**
     * Returns the count of linting items with WARNING severity.
     * <p>
     * This method filters the linting items and counts only those with
     * {@link LinterSeverity#WARN} severity level.
     * </p>
     *
     * @return the number of warning items in the linting output
     */
    public int getWarningCount() {
        return (int) LintItems.stream()
                .filter(item -> item.getSeverity() == LinterSeverity.WARN)
                .count();
    }

    /**
     * Returns the count of lint items with INFO severity.
     * <p>
     * This method filters the lint items and counts only those with
     * {@link LinterSeverity#INFO} severity level.
     * </p>
     *
     * @return the number of info items in the linting output
     */
    public int getInfoCount() {
        return (int) LintItems.stream()
                .filter(item -> item.getSeverity() == LinterSeverity.INFO)
                .count();
    }

    /**
     * Returns the count of lint items with SUCCESS severity.
     * <p>
     * This method filters the lint items and counts only those with
     * {@link LinterSeverity#SUCCESS} severity level.
     * </p>
     *
     * @return the number of success items in the linting output
     */
    public int getSuccessCount() {
        return (int) LintItems.stream()
                .filter(item -> item.getSeverity() == LinterSeverity.SUCCESS)
                .count();
    }


    /**
     * Retrieves the process ID from the first BPMN lint item.
     *
     * @return the process ID or "unknown_process".
     */
    public String getProcessId()
    {
        if (LintItems == null || LintItems.isEmpty())
            return "unknown_process";

        for (AbstractLintItem item : LintItems)
        {
            if (item instanceof BpmnElementLintItem bpmnItem)
            {
                String pid = bpmnItem.getProcessId();
                if (pid != null && !pid.isEmpty())
                    return pid;
            }
        }

        return "unknown_process";
    }

    /**
     * Creates an empty LintingOutput instance.
     *
     * @return an empty LintingOutput.
     */
    public static LintingOutput empty()
    {
        return new LintingOutput(List.of());
    }
}