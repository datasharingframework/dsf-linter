package dev.dsf.linter.output.item;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;

/**
 * Common interface for all lint items.
 * <p>
 * This interface defines the contract for lint results regardless of their
 * specific type (BPMN, FHIR, Plugin). Using an interface instead of an abstract
 * class allows for more flexibility in implementation and composition.
 * </p>
 */
public interface LintItem {

    /**
     * Returns the severity level of this lint item.
     *
     * @return the severity (ERROR, WARN, INFO, SUCCESS, etc.)
     */
    LinterSeverity getSeverity();

    /**
     * Returns a human-readable description of the lint issue.
     *
     * @return the description message
     */
    String getDescription();

    /**
     * Returns the type/category of this lint item.
     * This serves as a unique identifier for the type of issue.
     *
     * @return the linting type
     */
    LintingType getType();
}
