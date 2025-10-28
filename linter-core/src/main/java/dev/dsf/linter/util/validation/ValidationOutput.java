package dev.dsf.linter.util.validation;

import dev.dsf.linter.output.ValidationSeverity;
import dev.dsf.linter.output.item.AbstractValidationItem;
import dev.dsf.linter.output.item.BpmnElementValidationItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record ValidationOutput(List<AbstractValidationItem> validationItems)
{
    public static final Map<ValidationSeverity, Integer> SEVERITY_RANK = Map.of(
            ValidationSeverity.ERROR,   0,
            ValidationSeverity.WARN,    1,
            ValidationSeverity.INFO,    2,
            ValidationSeverity.SUCCESS, 3
    );

    public ValidationOutput(List<AbstractValidationItem> validationItems)
    {
        this.validationItems = new ArrayList<>(validationItems);
        this.validationItems.sort(
                Comparator.comparingInt((AbstractValidationItem i) ->
                                SEVERITY_RANK.getOrDefault(i.getSeverity(), Integer.MAX_VALUE))
                        .thenComparing(AbstractValidationItem::toString)
        );
    }

    /**
     * Returns the sorted list of validation items.
     *
     * @return a sorted, unmodifiable view of the validation items list.
     */
    @Override
    public List<AbstractValidationItem> validationItems()
    {
        return Collections.unmodifiableList(validationItems);
    }

    /**
     * Returns the count of validation items with ERROR severity.
     * <p>
     * This method filters the validation items and counts only those with
     * {@link ValidationSeverity#ERROR} severity level.
     * </p>
     *
     * @return the number of error items in the validation output
     */
    public int getErrorCount() {
        return (int) validationItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.ERROR)
                .count();
    }

    /**
     * Returns the count of validation items with WARNING severity.
     * <p>
     * This method filters the validation items and counts only those with
     * {@link ValidationSeverity#WARN} severity level.
     * </p>
     *
     * @return the number of warning items in the validation output
     */
    public int getWarningCount() {
        return (int) validationItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.WARN)
                .count();
    }

    /**
     * Returns the count of validation items with INFO severity.
     * <p>
     * This method filters the validation items and counts only those with
     * {@link ValidationSeverity#INFO} severity level.
     * </p>
     *
     * @return the number of info items in the validation output
     */
    public int getInfoCount() {
        return (int) validationItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.INFO)
                .count();
    }

    /**
     * Returns the count of validation items with SUCCESS severity.
     * <p>
     * This method filters the validation items and counts only those with
     * {@link ValidationSeverity#SUCCESS} severity level.
     * </p>
     *
     * @return the number of success items in the validation output
     */
    public int getSuccessCount() {
        return (int) validationItems.stream()
                .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                .count();
    }


    /**
     * Retrieves the process ID from the first BPMN validation item.
     *
     * @return the process ID or "unknown_process".
     */
    public String getProcessId()
    {
        if (validationItems == null || validationItems.isEmpty())
            return "unknown_process";

        for (AbstractValidationItem item : validationItems)
        {
            if (item instanceof BpmnElementValidationItem bpmnItem)
            {
                String pid = bpmnItem.getProcessId();
                if (pid != null && !pid.isEmpty())
                    return pid;
            }
        }

        return "unknown_process";
    }

    /**
     * Creates an empty ValidationOutput instance.
     *
     * @return an empty ValidationOutput.
     */
    public static ValidationOutput empty()
    {
        return new ValidationOutput(List.of());
    }
}