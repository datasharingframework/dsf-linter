package dev.dsf.utils.validator.util.validation;

import dev.dsf.utils.validator.ValidationSeverity;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.util.Console;

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
     * Prints the validation results to the console.
     */
    public void printResults(Logger logger)
    {
        List<AbstractValidationItem> projectWideSuccess = new ArrayList<>();
        List<AbstractValidationItem> leftoverResources = new ArrayList<>();
        List<AbstractValidationItem> metadataWarnings = new ArrayList<>();
        List<AbstractValidationItem> regularItems = new ArrayList<>();

        for (AbstractValidationItem item : validationItems) {
            if (item instanceof PluginDefinitionValidationItemSuccess) {
                projectWideSuccess.add(item);
            } else if (item instanceof PluginDefinitionProcessPluginRessourceNotLoadedValidationItem) {
                leftoverResources.add(item);
            } else if (item instanceof PluginDefinitionNoProcessModelDefinedValidationItem ||
                    item instanceof PluginDefinitionNoFhirResourcesDefinedValidationItem) {
                metadataWarnings.add(item);
            } else {
                regularItems.add(item);
            }
        }

        boolean hasAnyIssues = !leftoverResources.isEmpty() ||
                !metadataWarnings.isEmpty() ||
                hasNonSuccessItems(regularItems);

        if (!projectWideSuccess.isEmpty()) {
            printProjectWideSuccess(projectWideSuccess, logger);
        }

        if (!metadataWarnings.isEmpty()) {
            printPluginMetadataWarnings(metadataWarnings, logger);
        }

        if (!leftoverResources.isEmpty()) {
            printUnreferencedResources(leftoverResources, logger);
        }

        if (!regularItems.isEmpty()) {
            printRegularItems(regularItems, logger);
        }

        if (!hasAnyIssues && projectWideSuccess.isEmpty()) {
            Console.green("File validated successfully, no issues were detected.");
        }

        if (logger.isVerbose()) {
            List<AbstractValidationItem> otherSuccessItems = regularItems.stream()
                    .filter(item -> item.getSeverity() == ValidationSeverity.SUCCESS)
                    .filter(item -> !(item instanceof PluginDefinitionValidationItemSuccess))
                    .toList();

            if (!otherSuccessItems.isEmpty()) {
                logger.info("");
                Console.green(" Additional SUCCESS items (" + otherSuccessItems.size() + "):");
                otherSuccessItems.forEach(item -> Console.green("* " + item));
            }
        }
    }

    private void printProjectWideSuccess(List<AbstractValidationItem> items, Logger logger) {
        logger.info("");
        Console.green(" ✓ Project-Wide Validation");
        for (AbstractValidationItem item : items) {
            if (item instanceof PluginDefinitionValidationItemSuccess success) {
                Console.green("   ✓ " + success.getMessage());
            }
        }
    }

    private void printPluginMetadataWarnings(List<AbstractValidationItem> items, Logger logger) {
        logger.info("");
        Console.yellow(" ⚠ Plugin Metadata Warnings");

        for (AbstractValidationItem item : items) {
            if (item instanceof PluginDefinitionNoProcessModelDefinedValidationItem noModel) {
                Console.yellow("   ⚠ No BPMN process models defined");
                Console.yellow("     Location: " + noModel.getLocation());
                Console.yellow("     " + noModel.getMessage());
            } else if (item instanceof PluginDefinitionNoFhirResourcesDefinedValidationItem noFhir) {
                Console.yellow("   ⚠ No FHIR resources defined");
                Console.yellow("     Location: " + noFhir.getLocation());
                Console.yellow("     " + noFhir.getMessage());
            }
        }
    }

    private void printUnreferencedResources(List<AbstractValidationItem> items, Logger logger) {
        logger.info("");
        Console.yellow(" ⚠ Unreferenced Resources (Leftovers)");
        Console.yellow("   These files exist in the repository but are not referenced by ProcessPluginDefinition:");
        logger.info("");

        List<AbstractValidationItem> bpmnLeftovers = new ArrayList<>();
        List<AbstractValidationItem> fhirLeftovers = new ArrayList<>();

        for (AbstractValidationItem item : items) {
            if (item instanceof PluginDefinitionProcessPluginRessourceNotLoadedValidationItem leftover) {
                String location = leftover.getLocation();
                if (location.endsWith(".bpmn")) {
                    bpmnLeftovers.add(leftover);
                } else {
                    fhirLeftovers.add(leftover);
                }
            }
        }

        if (!bpmnLeftovers.isEmpty()) {
            Console.yellow("   BPMN files (" + bpmnLeftovers.size() + "):");
            for (AbstractValidationItem item : bpmnLeftovers) {
                if (item instanceof PluginDefinitionProcessPluginRessourceNotLoadedValidationItem leftover) {
                    Console.yellow("     • " + leftover.getLocation());
                }
            }
        }

        if (!fhirLeftovers.isEmpty()) {
            if (!bpmnLeftovers.isEmpty()) {
                logger.info("");
            }
            Console.yellow("   FHIR files (" + fhirLeftovers.size() + "):");
            for (AbstractValidationItem item : fhirLeftovers) {
                if (item instanceof PluginDefinitionProcessPluginRessourceNotLoadedValidationItem leftover) {
                    Console.yellow("     • " + leftover.getLocation());
                }
            }
        }
    }

    private void printRegularItems(List<AbstractValidationItem> items, Logger logger) {
        List<AbstractValidationItem> nonSuccessItems = items.stream()
                .filter(item -> item.getSeverity() != ValidationSeverity.SUCCESS)
                .toList();

        if (nonSuccessItems.isEmpty()) {
            return;
        }

        logger.info(" Found " + nonSuccessItems.size() + " issue(s):");

        ValidationSeverity currentSeverity = null;

        for (AbstractValidationItem item : nonSuccessItems) {
            if (currentSeverity != item.getSeverity()) {
                currentSeverity = item.getSeverity();
                printSeverityHeader(currentSeverity, logger);
            }

            printItemWithColor(item);
        }
    }

    private boolean hasNonSuccessItems(List<AbstractValidationItem> items) {
        return items.stream().anyMatch(item -> item.getSeverity() != ValidationSeverity.SUCCESS);
    }

    private void printSeverityHeader(ValidationSeverity severity, Logger logger) {
        logger.info("");
        String icon = switch (severity) {
            case ERROR -> "✗";
            case WARN -> "⚠";
            case INFO -> "ℹ";
            default -> "•";
        };

        String header = " " + icon + " " + severity + " items:";
        Console.printWithSeverity(header, severity.name());
    }

    private void printItemWithColor(AbstractValidationItem item) {
        String message = "* " + item.toString();
        Console.printWithSeverity(message, item.getSeverity().name());
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