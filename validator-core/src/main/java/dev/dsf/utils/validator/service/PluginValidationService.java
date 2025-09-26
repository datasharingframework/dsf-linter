package dev.dsf.utils.validator.service;

import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.item.*;
import dev.dsf.utils.validator.plugin.PluginDefinitionDiscovery.PluginAdapter;
import dev.dsf.utils.validator.util.api.ApiVersion;
import dev.dsf.utils.validator.exception.MissingServiceRegistrationException;

import java.io.File;
import java.nio.file.*;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for validating plugin-specific configurations.
 * Primarily checks for ServiceLoader registrations.
 */
public class PluginValidationService {

    private final Logger logger;

    public PluginValidationService(Logger logger) {
        this.logger = logger;
    }

    /**
     * Validates plugin configuration, particularly the ServiceLoader registration.
     *
     * @param projectPath   The project root path
     * @param pluginAdapter The plugin being validated
     * @param apiVersion    The API version of the plugin
     * @return Validation result containing success or error items
     * @throws MissingServiceRegistrationException if ServiceLoader registration is missing
     */
    public ValidationResult validatePlugin(Path projectPath, PluginAdapter pluginAdapter, ApiVersion apiVersion)
            throws MissingServiceRegistrationException {

        String pluginName = pluginAdapter != null
                ? pluginAdapter.sourceClass().getSimpleName()
                : "unknown plugin";

        logger.info("Validating plugin configuration for: " + pluginName);

        List<AbstractValidationItem> items = new ArrayList<>();
        String projectName = projectPath.getFileName() != null
                ? projectPath.getFileName().toString()
                : "project";

        // Determine expected service file based on API version
        String expectedServiceFile = determineExpectedServiceFile(pluginAdapter, apiVersion);

        // Search for the service file
        boolean found = findServiceFile(projectPath, expectedServiceFile);

        if (found) {
            // Success: Service registration found
            items.add(createSuccessItem(projectName, apiVersion, pluginAdapter));
            logger.info("✓ ServiceLoader registration verified");
        } else {
            // Error: Service registration missing
            items.add(createErrorItem(projectName, expectedServiceFile));
            logger.error("✗ Missing ServiceLoader registration");

            throw new MissingServiceRegistrationException(
                    formatMissingRegistrationMessage(pluginAdapter, projectPath)
            );
        }

        return new ValidationResult(items);
    }

    /**
     * Determines the expected service file name based on API version and plugin information.
     */
    private String determineExpectedServiceFile(PluginAdapter pluginAdapter, ApiVersion apiVersion) {
        // Priority 1: Use explicit API version if available
        if (apiVersion != null) {
            return switch (apiVersion) {
                case V1 -> "dev.dsf.bpe.v1.ProcessPluginDefinition";
                case V2 -> "dev.dsf.bpe.v2.ProcessPluginDefinition";
                default -> null;
            };
        }

        // Priority 2: Infer from plugin class name
        if (pluginAdapter != null) {
            String className = pluginAdapter.sourceClass().getName();
            if (className.contains(".v1.")) {
                return "dev.dsf.bpe.v1.ProcessPluginDefinition";
            } else if (className.contains(".v2.")) {
                return "dev.dsf.bpe.v2.ProcessPluginDefinition";
            }
        }

        // Unable to determine specific version
        return null;
    }

    /**
     * Searches for the service file in the project.
     */
    private boolean findServiceFile(Path projectPath, String expectedServiceFile) {
        // Try standard locations first
        if (checkStandardLocations(projectPath, expectedServiceFile)) {
            return true;
        }

        // Fallback to recursive search
        return performRecursiveSearch(projectPath, expectedServiceFile);
    }

    /**
     * Checks standard META-INF/services locations.
     */
    private boolean checkStandardLocations(Path projectPath, String expectedServiceFile) {
        List<Path> standardPaths = buildSearchPaths(projectPath);

        if (expectedServiceFile != null) {
            // Look for specific service file
            for (Path servicesDir : standardPaths) {
                if (Files.exists(servicesDir.resolve(expectedServiceFile))) {
                    logger.debug("Found service file at: " + servicesDir);
                    return true;
                }
            }
        } else {
            // Look for any ProcessPluginDefinition service file
            for (Path servicesDir : standardPaths) {
                if (containsProcessPluginDefinitionFile(servicesDir)) {
                    logger.debug("Found service file at: " + servicesDir);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Builds list of standard paths to search for service files.
     */
    private List<Path> buildSearchPaths(Path projectPath) {
        List<Path> paths = new ArrayList<>();

        // Direct project paths
        paths.add(projectPath.resolve("META-INF/services"));
        paths.add(projectPath.resolve("src/main/resources/META-INF/services"));
        paths.add(projectPath.resolve("target/classes/META-INF/services"));
        paths.add(projectPath.resolve("build/classes/java/main/META-INF/services"));
        paths.add(projectPath.resolve("build/resources/main/META-INF/services"));

        // Check subdirectories (for multi-module projects)
        try (var stream = Files.list(projectPath)) {
            List<Path> submodulePaths = stream
                    .filter(Files::isDirectory)
                    .filter(dir -> !isIgnoredDirectory(dir))
                    .flatMap(module -> buildModuleServicePaths(module).stream())
                    .toList();
            paths.addAll(submodulePaths);
        } catch (IOException e) {
            logger.debug("Could not scan subdirectories: " + e.getMessage());
        }

        return paths;
    }

    /**
     * Builds service paths for a specific module.
     */
    private List<Path> buildModuleServicePaths(Path modulePath) {
        return List.of(
                modulePath.resolve("src/main/resources/META-INF/services"),
                modulePath.resolve("target/classes/META-INF/services"),
                modulePath.resolve("build/classes/java/main/META-INF/services"),
                modulePath.resolve("build/resources/main/META-INF/services")
        );
    }

    /**
     * Checks if a directory contains any ProcessPluginDefinition service file.
     */
    private boolean containsProcessPluginDefinitionFile(Path servicesDir) {
        if (!Files.exists(servicesDir) || !Files.isDirectory(servicesDir)) {
            return false;
        }

        Set<String> knownServiceFiles = Set.of(
                "dev.dsf.bpe.v1.ProcessPluginDefinition",
                "dev.dsf.bpe.v2.ProcessPluginDefinition",
                "dev.dsf.ProcessPluginDefinition"
        );

        try (var stream = Files.list(servicesDir)) {
            return stream.anyMatch(file -> {
                Path fileName = file.getFileName();
                return fileName != null && knownServiceFiles.contains(fileName.toString());
            });
        } catch (IOException e) {
            logger.debug("Could not list files in: " + servicesDir);
            return false;
        }
    }

    /**
     * Performs recursive search for service files as last resort.
     */
    private boolean performRecursiveSearch(Path root, String expectedServiceFile) {
        try {
            ServiceFileVisitor visitor = new ServiceFileVisitor(expectedServiceFile);
            Files.walkFileTree(root, Set.of(), 5, visitor);
            return visitor.isFound();
        } catch (IOException e) {
            logger.debug("Error during recursive search: " + e.getMessage());
            return false;
        }
    }

    /**
     * File visitor for finding service files.
     */
    private static class ServiceFileVisitor extends SimpleFileVisitor<Path> {
        private final String targetFile;
        private boolean found = false;

        ServiceFileVisitor(String targetFile) {
            this.targetFile = targetFile;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (isServiceFile(file)) {
                found = true;
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return isIgnoredDirectory(dir)
                    ? FileVisitResult.SKIP_SUBTREE
                    : FileVisitResult.CONTINUE;
        }

        private boolean isServiceFile(Path file) {
            Path parent = file.getParent();
            if (!isMetaInfServices(parent)) {
                return false;
            }

            Path fileName = file.getFileName();
            if (fileName == null) {
                return false;
            }

            String name = fileName.toString();
            return targetFile != null
                    ? name.equals(targetFile)
                    : name.contains("ProcessPluginDefinition");
        }

        private boolean isMetaInfServices(Path dir) {
            if (dir == null) return false;

            Path dirName = dir.getFileName();
            if (dirName == null || !"services".equals(dirName.toString())) {
                return false;
            }

            Path parent = dir.getParent();
            if (parent == null) return false;

            Path parentName = parent.getFileName();
            return parentName != null && "META-INF".equals(parentName.toString());
        }

        boolean isFound() {
            return found;
        }
    }

    /**
     * Checks if a directory should be ignored during traversal.
     */
    private static boolean isIgnoredDirectory(Path dir) {
        Path dirName = dir.getFileName();
        if (dirName == null) return false;

        String name = dirName.toString();
        return name.startsWith(".")
                || name.equals("node_modules")
                || name.equals(".git")
                || name.equals(".idea")
                || name.equals(".gradle");
    }

    /**
     * Creates success validation item.
     */
    private AbstractValidationItem createSuccessItem(String projectName, ApiVersion apiVersion, PluginAdapter adapter) {
        StringBuilder message = new StringBuilder("ServiceLoader registration found");

        if (apiVersion != null) {
            message.append(" (API ").append(apiVersion).append(")");
        }

        if (adapter != null) {
            message.append(" for ").append(adapter.sourceClass().getSimpleName());
        }

        return new PluginValidationItemSuccess(
                new File(projectName),
                "META-INF/services",
                message.toString()
        );
    }

    /**
     * Creates error validation item.
     */
    private AbstractValidationItem createErrorItem(String projectName, String expectedFile) {
        String message = "ServiceLoader registration not found";

        if (expectedFile != null) {
            message += " (expected: " + expectedFile + ")";
        }

        return new MissingServiceLoaderRegistrationValidationItem(
                new File(projectName),
                "META-INF/services",
                message
        );
    }

    /**
     * Formats the exception message for missing registration.
     */
    private String formatMissingRegistrationMessage(PluginAdapter adapter, Path projectPath) {
        String pluginInfo = adapter != null
                ? adapter.sourceClass().getName()
                : "ProcessPluginDefinition";

        return String.format(
                "No ServiceLoader registration found for %s in %s",
                pluginInfo,
                projectPath
        );
    }

    /**
     * Validation result containing items and statistics.
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

        public List<AbstractValidationItem> getItems() {
            return items;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public int getWarningCount() {
            return warningCount;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public boolean hasErrors() {
            return errorCount > 0;
        }

        public boolean hasWarnings() {
            return warningCount > 0;
        }
    }
}