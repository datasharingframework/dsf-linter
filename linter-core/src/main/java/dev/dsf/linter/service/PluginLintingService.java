package dev.dsf.linter.service;

import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.output.item.PluginDefinitionMissingServiceLoaderRegistrationLintItem;
import dev.dsf.linter.output.item.PluginDefinitionLintItemSuccess;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.exception.MissingServiceRegistrationException;

import java.io.File;
import java.nio.file.*;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.dsf.linter.constants.DsfApiConstants.V1_SERVICE_FILE;
import static dev.dsf.linter.constants.DsfApiConstants.V2_SERVICE_FILE;

/**
 * Service for linting plugin-specific configurations.
 * Primarily checks for ServiceLoader registrations and collects all plugin-related items.
 */
public class PluginLintingService {

    private final Logger logger;

    public PluginLintingService(Logger logger) {
        this.logger = logger;
    }

    /**
     * lints plugin configuration, including ServiceLoader registration and collected plugin items.
     *
     * @param projectPath        The project root path
     * @param pluginAdapter      The plugin being linted
     * @param apiVersion         The API version of the plugin
     * @param collectedPluginItems Plugin-level items collected from BPMN/FHIR linting
     * @return Linter result containing all plugin lint items
     * @throws MissingServiceRegistrationException if ServiceLoader registration is missing
     */
    public LintingResult lintPlugin(Path projectPath,
                                    PluginAdapter pluginAdapter,
                                    ApiVersion apiVersion,
                                    List<AbstractLintItem> collectedPluginItems)
            throws MissingServiceRegistrationException {

        List<AbstractLintItem> items = new ArrayList<>();

        if (collectedPluginItems != null && !collectedPluginItems.isEmpty()) {
            items.addAll(collectedPluginItems);
        }

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
        } else {
            // Error: Service registration missing
            items.add(createErrorItem(projectName, expectedServiceFile));
            throw new MissingServiceRegistrationException(
                    formatMissingRegistrationMessage(pluginAdapter, projectPath)
            );
        }

        return new LintingResult(items);
    }


    /**
     * Determines the expected service file name based on API version and plugin information.
     */
    private String determineExpectedServiceFile(PluginAdapter pluginAdapter, ApiVersion apiVersion) {
        // Priority 1: Use explicit API version if available
        if (apiVersion != null) {
            return switch (apiVersion) {
                case V1 -> V1_SERVICE_FILE;
                case V2 -> V2_SERVICE_FILE;
                default -> null;
            };
        }

        // Priority 2: Infer from plugin class name
        if (pluginAdapter != null) {
            String className = pluginAdapter.sourceClass().getName();
            if (className.contains(".v1.")) {
                return V1_SERVICE_FILE;
            } else if (className.contains(".v2.")) {
                return V2_SERVICE_FILE;
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
                    logger.debug("Found service file under project root: " + projectPath.toAbsolutePath());
                    return true;
                }
            }
        } else {
            // Look for any ProcessPluginDefinition service file
            for (Path servicesDir : standardPaths) {
                if (containsProcessPluginDefinitionFile(servicesDir)) {
                    logger.debug("Found service file under project root: " + projectPath.toAbsolutePath());
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
                V1_SERVICE_FILE,
                V2_SERVICE_FILE,
                "dev.dsf.ProcessPluginDefinition" // Legacy fallback
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
     * Creates success lint item.
     */
    private AbstractLintItem createSuccessItem(String projectName, ApiVersion apiVersion, PluginAdapter adapter) {
        StringBuilder message = new StringBuilder("ServiceLoader registration found");

        if (apiVersion != null) {
            message.append(" (API ").append(apiVersion).append(")");
        }

        if (adapter != null) {
            message.append(" for ").append(adapter.sourceClass().getSimpleName());
        }

        return new PluginDefinitionLintItemSuccess(
                new File(projectName),
                "META-INF/services",
                message.toString()
        );
    }

    /**
     * Creates error lint item.
     */
    private AbstractLintItem createErrorItem(String projectName, String expectedFile) {
        String message = "ServiceLoader registration not found";

        if (expectedFile != null) {
            message += " (expected: " + expectedFile + ")";
        }

        return new PluginDefinitionMissingServiceLoaderRegistrationLintItem(
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
}