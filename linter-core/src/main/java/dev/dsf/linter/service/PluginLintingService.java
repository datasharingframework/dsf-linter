package dev.dsf.linter.service;

import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.output.item.PluginLintItem;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.exception.MissingServiceRegistrationException;

import org.jetbrains.annotations.NotNull;

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
 */
public class PluginLintingService {

    private final Logger logger;

    public PluginLintingService(Logger logger) {
        this.logger = logger;
    }

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

        // Validate resource version
        validateResourceVersion(pluginAdapter, projectName, items);

        String expectedServiceFile = determineExpectedServiceFile(pluginAdapter, apiVersion);
        boolean found = findServiceFile(projectPath, expectedServiceFile);

        if (found) {
            items.add(createSuccessItem(projectName, apiVersion, pluginAdapter));
        } else {
            items.add(createErrorItem(projectName, expectedServiceFile));
            throw new MissingServiceRegistrationException(
                    formatMissingRegistrationMessage(pluginAdapter, projectPath)
            );
        }

        return new LintingResult(items);
    }

    /**
     * Validates the resource version of the plugin.
     * <p>
     * The resource version is derived from the plugin version using the pattern
     * {@code (?<resourceVersion>\d+\.\d+)\.\d+\.\d+}. If the plugin version does not
     * match this pattern, getResourceVersion() returns null and an error is reported.
     * </p>
     *
     * @param pluginAdapter the plugin adapter to validate
     * @param projectName   the project name for error reporting
     * @param items         the list to add validation items to
     */
    private void validateResourceVersion(PluginAdapter pluginAdapter, String projectName, List<AbstractLintItem> items) {
        if (pluginAdapter == null) {
            return;
        }

        try {
            String resourceVersion = pluginAdapter.getResourceVersion();

            if (resourceVersion == null) {
                String description = String.format(
                        "Plugin '%s': getResourceVersion() returned null. " +
                        "The plugin version must match the pattern 'd.d.d.d' (e.g., '1.0.0.1') " +
                        "to derive a valid resource version (e.g., '1.0').",
                        pluginAdapter.getName()
                );
                items.add(new PluginLintItem(
                        LinterSeverity.ERROR,
                        LintingType.PLUGIN_DEFINITION_RESOURCE_VERSION_NULL,
                        new File(projectName),
                        pluginAdapter.getName(),
                        description
                ));
            } else {
                items.add(new PluginLintItem(
                        LinterSeverity.SUCCESS,
                        LintingType.SUCCESS,
                        new File(projectName),
                        pluginAdapter.getName(),
                        String.format("Plugin '%s': Resource version '%s' is valid.",
                                pluginAdapter.getName(), resourceVersion)
                ));
            }
        } catch (Exception e) {
            logger.debug("Could not validate resource version for plugin: " + e.getMessage());
        }
    }

    private String determineExpectedServiceFile(PluginAdapter pluginAdapter, ApiVersion apiVersion) {
        if (apiVersion != null) {
            return switch (apiVersion) {
                case V1 -> V1_SERVICE_FILE;
                case V2 -> V2_SERVICE_FILE;
                default -> null;
            };
        }

        if (pluginAdapter != null) {
            String className = pluginAdapter.sourceClass().getName();
            if (className.contains(".v1.")) {
                return V1_SERVICE_FILE;
            } else if (className.contains(".v2.")) {
                return V2_SERVICE_FILE;
            }
        }
        return null;
    }

    private boolean findServiceFile(Path projectPath, String expectedServiceFile) {
        if (checkStandardLocations(projectPath, expectedServiceFile)) {
            return true;
        }
        return performRecursiveSearch(projectPath, expectedServiceFile);
    }

    private boolean checkStandardLocations(Path projectPath, String expectedServiceFile) {
        List<Path> standardPaths = buildSearchPaths(projectPath);

        if (expectedServiceFile != null) {
            for (Path servicesDir : standardPaths) {
                if (Files.exists(servicesDir.resolve(expectedServiceFile))) {
                    logger.debug("Found service file under project root: " + projectPath.toAbsolutePath());
                    return true;
                }
            }
        } else {
            for (Path servicesDir : standardPaths) {
                if (containsProcessPluginDefinitionFile(servicesDir)) {
                    logger.debug("Found service file under project root: " + projectPath.toAbsolutePath());
                    return true;
                }
            }
        }

        return false;
    }

    private List<Path> buildSearchPaths(Path projectPath) {
        List<Path> paths = new ArrayList<>();

        paths.add(projectPath.resolve("META-INF/services"));
        paths.add(projectPath.resolve("src/main/resources/META-INF/services"));
        paths.add(projectPath.resolve("target/classes/META-INF/services"));
        paths.add(projectPath.resolve("build/classes/java/main/META-INF/services"));
        paths.add(projectPath.resolve("build/resources/main/META-INF/services"));

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

    private List<Path> buildModuleServicePaths(Path modulePath) {
        return List.of(
                modulePath.resolve("src/main/resources/META-INF/services"),
                modulePath.resolve("target/classes/META-INF/services"),
                modulePath.resolve("build/classes/java/main/META-INF/services"),
                modulePath.resolve("build/resources/main/META-INF/services")
        );
    }

    private boolean containsProcessPluginDefinitionFile(Path servicesDir) {
        if (!Files.exists(servicesDir) || !Files.isDirectory(servicesDir)) {
            return false;
        }

        Set<String> knownServiceFiles = Set.of(
                V1_SERVICE_FILE,
                V2_SERVICE_FILE,
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

    private static class ServiceFileVisitor extends SimpleFileVisitor<Path> {
        private final String targetFile;
        private boolean found = false;

        ServiceFileVisitor(String targetFile) {
            this.targetFile = targetFile;
        }

        @Override
        public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
            if (isServiceFile(file)) {
                found = true;
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) {
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

    private AbstractLintItem createSuccessItem(String projectName, ApiVersion apiVersion, PluginAdapter adapter) {
        StringBuilder message = new StringBuilder("ServiceLoader registration found");

        if (apiVersion != null) {
            message.append(" (API ").append(apiVersion).append(")");
        }

        if (adapter != null) {
            message.append(" for ").append(adapter.sourceClass().getSimpleName());
        }

        return PluginLintItem.success(new File(projectName), "META-INF/services", message.toString());
    }

    private AbstractLintItem createErrorItem(String projectName, String expectedFile) {
        String message = "ServiceLoader registration not found";

        if (expectedFile != null) {
            message += " (expected: " + expectedFile + ")";
        }

        return new PluginLintItem(
                LinterSeverity.ERROR,
                LintingType.PLUGIN_DEFINITION_MISSING_SERVICE_LOADER_REGISTRATION,
                new File(projectName),
                "META-INF/services",
                message
        );
    }

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
