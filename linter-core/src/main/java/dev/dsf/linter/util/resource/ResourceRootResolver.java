package dev.dsf.linter.util.resource;

import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for resolving resource root directories in extracted JAR projects.
 * <p>
 * This resolver supports extracted JAR structures where all classes and resources
 * are in a flat directory structure. It uses CodeSource-based detection for plugin
 * classes when available, otherwise falls back to the project root.
 * </p>
 *
 * @see ResolutionResult
 * @see ResolutionStrategy
 */
public final class ResourceRootResolver {

    private ResourceRootResolver() {
        // Utility class
    }

    /**
     * Result of resource root resolution.
     */
    public record ResolutionResult(File resourceRoot, ResolutionStrategy strategy, String description) {

        @Override
        public @NotNull String toString() {
            return String.format("ResourceRoot[%s]: %s (%s)",
                    strategy, resourceRoot.getAbsolutePath(), description);
        }
    }

    /**
     * Enumeration of resolution strategies.
     */
    public enum ResolutionStrategy {
        CODE_SOURCE_DIRECTORY,
        PROJECT_ROOT_FALLBACK
    }

    /**
     * Determines the resource root directory for a project with optional plugin context.
     * <p>
     * For extracted JAR structures, this tries to detect the directory from the plugin's
     * CodeSource location. If that fails or no plugin adapter is provided, it falls back
     * to using the project root directory.
     * </p>
     *
     * @param projectDir the project directory (extracted JAR root)
     * @param pluginAdapter optional plugin adapter for CodeSource-based detection
     * @return resolution result with the determined resource root
     */
    public static ResolutionResult resolveResourceRoot(File projectDir, PluginAdapter pluginAdapter) {
        if (projectDir == null) {
            throw new IllegalArgumentException("projectDir cannot be null");
        }

        if (pluginAdapter != null) {
            ResolutionResult codeSourceResult = tryCodeSourceResolution(pluginAdapter);
            if (codeSourceResult != null) {
                return codeSourceResult;
            }
        }

        return new ResolutionResult(
                projectDir,
                ResolutionStrategy.PROJECT_ROOT_FALLBACK,
                "Using project root for extracted JAR"
        );
    }


    /**
     * Resolves plugin-specific resource root directory.
     * <p>
     * This is the primary method for linting scenarios where each plugin
     * needs its own resource root tracking.
     * </p>
     *
     * @param projectDir the project directory (extracted JAR root)
     * @param pluginAdapter the plugin adapter for CodeSource-based detection
     * @return resolution result with plugin-specific resource root
     */
    public static ResolutionResult resolveResourceRootForPlugin(
            File projectDir,
            PluginAdapter pluginAdapter) {

        if (projectDir == null) {
            throw new IllegalArgumentException("projectDir cannot be null");
        }

        if (pluginAdapter == null) {
            return resolveResourceRoot(projectDir, null);
        }

        ResolutionResult codeSourceResult = tryCodeSourceResolution(pluginAdapter);
        if (codeSourceResult != null) {
            return codeSourceResult;
        }

        return resolveResourceRoot(projectDir, null);
    }

    /**
     * Attempts to resolve resource root from plugin class CodeSource.
     * <p>
     * For extracted JARs, the CodeSource typically points to the extraction directory.
     * </p>
     */
    private static ResolutionResult tryCodeSourceResolution(PluginAdapter pluginAdapter) {
        try {
            Class<?> pluginClass = pluginAdapter.sourceClass();
            if (pluginClass == null) {
                return null;
            }

            java.security.ProtectionDomain pd = pluginClass.getProtectionDomain();
            if (pd == null || pd.getCodeSource() == null || pd.getCodeSource().getLocation() == null) {
                return null;
            }

            java.net.URI uri = pd.getCodeSource().getLocation().toURI();
            Path loc = Paths.get(uri);

            if (!Files.isDirectory(loc)) {
                return null;
            }

            return new ResolutionResult(
                    loc.toFile(),
                    ResolutionStrategy.CODE_SOURCE_DIRECTORY,
                    "Detected from plugin CodeSource (extracted JAR directory)"
            );

        } catch (Exception e) {
            return null;
        }
    }
}