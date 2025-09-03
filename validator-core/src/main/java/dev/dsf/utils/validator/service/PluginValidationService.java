package dev.dsf.utils.validator.service;

import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.util.api.ApiRegistrationValidationSupport;
import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for plugin-specific validations.
 *
 * <p>This service handles:
 * <ul>
 *   <li>ServiceLoader registration verification</li>
 *   <li>Plugin configuration validation</li>
 *   <li>ProcessPluginDefinition registration checks</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class PluginValidationService {

    private final Logger logger;
    private final ApiRegistrationValidationSupport registrationSupport;

    /**
     * Constructs a new PluginValidationService.
     *
     * @param logger the logger for output messages
     */
    public PluginValidationService(Logger logger) {
        this.logger = logger;
        this.registrationSupport = new ApiRegistrationValidationSupport();
    }

    /**
     * Validates plugin-specific aspects of the project.
     *
     * @param projectPath the path to the project
     * @return validation result containing all items
     * @throws MissingServiceRegistrationException if ServiceLoader registration is missing
     */
    public ValidationResult validate(Path projectPath) throws MissingServiceRegistrationException {
        logger.info("Validating plugin configuration...");

        List<AbstractValidationItem> items = collectPluginItems(projectPath);

        return new ValidationResult(items);
    }

    /**
     * Collects plugin validation items for the given project.
     *
     * @param projectPath the project path
     * @return list of plugin validation items
     * @throws MissingServiceRegistrationException if registration checks fail
     */
    private List<AbstractValidationItem> collectPluginItems(Path projectPath)
            throws MissingServiceRegistrationException {

        List<AbstractValidationItem> raw = new ArrayList<>();

        // Run registration validation
        registrationSupport.run("Plugin collection", projectPath, raw);

        // Convert raw items to proper PluginValidationItem types
        List<AbstractValidationItem> pluginItems = new ArrayList<>();

        // Use only the directory name for the "file" property
        String projectName = projectPath.toFile().getName();

        if (raw.isEmpty()) {
            // No registration found - create missing service item
            pluginItems.add(new MissingServiceLoaderRegistrationValidationItem(
                    new File(projectName),
                    "META-INF/services",
                    "No ProcessPluginDefinition service registration found"
            ));

            logger.warn("Missing ServiceLoader registration for ProcessPluginDefinition");
        } else {
            // Use the items directly from the support class
            pluginItems.addAll(raw);

            // Log summary of plugin validation
            long errorCount = raw.stream()
                    .filter(item -> item.getSeverity() == dev.dsf.utils.validator.ValidationSeverity.ERROR)
                    .count();

            long warningCount = raw.stream()
                    .filter(item -> item.getSeverity() == dev.dsf.utils.validator.ValidationSeverity.WARN)
                    .count();

            if (errorCount > 0) {
                logger.error("Plugin validation found " + errorCount + " error(s)");
            }
            if (warningCount > 0) {
                logger.warn("Plugin validation found " + warningCount + " warning(s)");
            }
        }

        return pluginItems;
    }

    /**
     * Data class containing validation results.
     */
    public static class ValidationResult {
        private final List<AbstractValidationItem> items;
        private final int errorCount;
        private final int warningCount;
        private final int successCount;

        public ValidationResult(List<AbstractValidationItem> items) {
            this.items = items;

            this.errorCount = (int) items.stream()
                    .filter(item -> item.getSeverity() == dev.dsf.utils.validator.ValidationSeverity.ERROR)
                    .count();

            this.warningCount = (int) items.stream()
                    .filter(item -> item.getSeverity() == dev.dsf.utils.validator.ValidationSeverity.WARN)
                    .count();

            this.successCount = (int) items.stream()
                    .filter(item -> item.getSeverity() == dev.dsf.utils.validator.ValidationSeverity.SUCCESS)
                    .count();
        }

        public List<AbstractValidationItem> getItems() { return items; }
        public int getErrorCount() { return errorCount; }
        public int getWarningCount() { return warningCount; }
        public int getSuccessCount() { return successCount; }

        public boolean hasErrors() { return errorCount > 0; }
        public boolean hasWarnings() { return warningCount > 0; }
    }
}