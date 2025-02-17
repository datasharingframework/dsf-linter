package dev.dsf.utils.validator;

import dev.dsf.utils.validator.item.AbstractValidationItem;

import java.util.List;

public class ValidationOutput {
    private final List<AbstractValidationItem> validationItems;

    public ValidationOutput(List<AbstractValidationItem> validationItems) {
        this.validationItems = validationItems;
    }

    public List<AbstractValidationItem> getValidationItems() {
        return validationItems;
    }

    /**
     * Prints validation results to the console.
     */
    public void printResults() {
        if (validationItems.isEmpty()) {
            System.out.println("✅ No issues found.");
        } else {
            System.out.println("⚠️ Found " + validationItems.size() + " issue(s):");
            for (AbstractValidationItem item : validationItems) {
                System.out.println("🔹 " + item);
            }
        }
    }
}
