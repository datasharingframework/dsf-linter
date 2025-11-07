package dev.dsf.linter.util.resource;

import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for resolving resource root directories in Java projects.
 * <p>
 * This resolver supports multiple project structures (Maven and Gradle) and uses various
 * resolution strategies to locate the appropriate resource directory. It can detect resource
 * roots from:
 * <ul>
 *   <li>CodeSource location of plugin classes</li>
 *   <li>Maven project structures (target/classes, src/main/resources)</li>
 *   <li>Gradle project structures (build/resources/main, src/main/resources)</li>
 *   <li>Package-based module resolution for multi-module projects</li>
 * </ul>
 * </p>
 * <p>
 * The resolution process follows a priority order, attempting multiple strategies until
 * a valid resource root is found. If no specific structure is detected, it falls back
 * to using the project root directory.
 * </p>
 *
 * @see ResolutionStrategy
 * @see ResolutionResult
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
        CODE_SOURCE_MAVEN,
        CODE_SOURCE_GRADLE,
        CODE_SOURCE_DIRECT,
        MAVEN_TARGET_CLASSES,
        MAVEN_SOURCE_RESOURCES,
        GRADLE_BUILD_RESOURCES,
        GRADLE_SOURCE_RESOURCES,
        PROJECT_ROOT_FALLBACK
    }

    /**
     * Determines the resource root directory for a project with optional plugin context.
     */
    public static ResolutionResult resolveResourceRoot(File projectDir, PluginAdapter pluginAdapter) {
        if (projectDir == null) {
            throw new IllegalArgumentException("projectDir cannot be null");
        }

        // Strategy 1: Try CodeSource-based detection if plugin adapter is available
        if (pluginAdapter != null) {
            ResolutionResult codeSourceResult = tryCodeSourceResolution(pluginAdapter);
            if (codeSourceResult != null) {
                return codeSourceResult;
            }
        }

        // Strategy 2: Try Maven structure
        ResolutionResult mavenResult = tryMavenStructure(projectDir);
        if (mavenResult != null) {
            return mavenResult;
        }

        // Strategy 3: Try Gradle structure
        ResolutionResult gradleResult = tryGradleStructure(projectDir);
        if (gradleResult != null) {
            return gradleResult;
        }

        // Strategy 4: Fallback to project root
        return new ResolutionResult(
                projectDir,
                ResolutionStrategy.PROJECT_ROOT_FALLBACK,
                "Using project root as last resort"
        );
    }

    /**
     * Simplified overload without plugin adapter.
     */
    public static ResolutionResult resolveResourceRoot(File projectDir) {
        return resolveResourceRoot(projectDir, null);
    }

    /**
     * Resolves plugin-specific resource root directory.
     * This is the primary method for linting scenarios where each plugin
     * needs its own resource root tracking.
     *
     * @param projectDir the project directory
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

        Class<?> pluginClass = pluginAdapter.sourceClass();
        if (pluginClass != null) {
            ResolutionResult packageBasedResult = tryPackageBasedResolution(projectDir, pluginClass);
            if (packageBasedResult != null) {
                return packageBasedResult;
            }
        }

        return resolveResourceRoot(projectDir, null);
    }

    /**
     * Attempts to resolve resource root from plugin class CodeSource.
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
                return null; // CodeSource points to JAR, not directory
            }

            String norm = loc.toString().replace('\\', '/');

            // Maven: <module>/target/classes
            if (norm.endsWith("/target/classes")) {
                return new ResolutionResult(
                        loc.toFile(),
                        ResolutionStrategy.CODE_SOURCE_MAVEN,
                        "Detected from plugin CodeSource (Maven)"
                );
            }

            // Gradle: <module>/build/classes/java/main → prefer resources/main
            if (norm.endsWith("/build/classes/java/main")) {
                Path gradleRes = loc.getParent()
                        .getParent()
                        .resolve("resources")
                        .resolve("main");

                if (Files.isDirectory(gradleRes)) {
                    return new ResolutionResult(
                            gradleRes.toFile(),
                            ResolutionStrategy.CODE_SOURCE_GRADLE,
                            "Detected from plugin CodeSource (Gradle resources)"
                    );
                }

                // Fall back to classes dir if resources dir is missing
                return new ResolutionResult(
                        loc.toFile(),
                        ResolutionStrategy.CODE_SOURCE_GRADLE,
                        "Detected from plugin CodeSource (Gradle classes, no resources dir)"
                );
            }

            // Unknown layout but still a directory on classpath
            return new ResolutionResult(
                    loc.toFile(),
                    ResolutionStrategy.CODE_SOURCE_DIRECT,
                    "Detected from plugin CodeSource (unknown layout)"
            );

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempts to resolve resource root based on plugin package structure.
     * This helps in multi-module projects where each plugin has its own module.
     */
    private static ResolutionResult tryPackageBasedResolution(File projectDir, Class<?> pluginClass) {
        try {
            String packageName = pluginClass.getPackage().getName();
            String[] packageParts = packageName.split("\\.");

            if (packageParts.length < 2) {
                return null;
            }

            String moduleName = packageParts[packageParts.length - 1];

            Path moduleDir = projectDir.toPath().resolve(moduleName);
            if (!Files.isDirectory(moduleDir)) {
                return null;
            }

            Path mavenClasses = moduleDir.resolve("target").resolve("classes");
            if (Files.isDirectory(mavenClasses)) {
                return new ResolutionResult(
                        mavenClasses.toFile(),
                        ResolutionStrategy.MAVEN_TARGET_CLASSES,
                        "Detected from package-based module resolution (Maven)"
                );
            }

            Path gradleResources = moduleDir.resolve("build").resolve("resources").resolve("main");
            if (Files.isDirectory(gradleResources)) {
                return new ResolutionResult(
                        gradleResources.toFile(),
                        ResolutionStrategy.GRADLE_BUILD_RESOURCES,
                        "Detected from package-based module resolution (Gradle)"
                );
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempts to resolve resource root using Maven project structure.
     */
    private static ResolutionResult tryMavenStructure(File projectDir) {
        // Check if pom.xml exists
        boolean isMavenProject = new File(projectDir, "pom.xml").exists();

        if (!isMavenProject) {
            return null;
        }

        // Prefer target/classes for compiled resources
        File targetClasses = new File(projectDir, "target/classes");
        if (targetClasses.isDirectory()) {
            return new ResolutionResult(
                    targetClasses,
                    ResolutionStrategy.MAVEN_TARGET_CLASSES,
                    "Maven project with compiled classes"
            );
        }

        // Fallback to src/main/resources for source checkout
        File srcMainResources = new File(projectDir, "src/main/resources");
        if (srcMainResources.isDirectory()) {
            return new ResolutionResult(
                    srcMainResources,
                    ResolutionStrategy.MAVEN_SOURCE_RESOURCES,
                    "Maven project with source resources (not yet compiled)"
            );
        }

        return null;
    }

    /**
     * Attempts to resolve resource root using Gradle project structure.
     */
    private static ResolutionResult tryGradleStructure(File projectDir) {
        // Check if build.gradle or build.gradle.kts exists
        boolean isGradleProject = new File(projectDir, "build.gradle").exists()
                || new File(projectDir, "build.gradle.kts").exists();

        if (!isGradleProject) {
            return null;
        }

        // Prefer build/resources/main for compiled resources
        File buildResources = new File(projectDir, "build/resources/main");
        if (buildResources.isDirectory()) {
            return new ResolutionResult(
                    buildResources,
                    ResolutionStrategy.GRADLE_BUILD_RESOURCES,
                    "Gradle project with compiled resources"
            );
        }

        // Fallback to src/main/resources for source checkout
        File srcMainResources = new File(projectDir, "src/main/resources");
        if (srcMainResources.isDirectory()) {
            return new ResolutionResult(
                    srcMainResources,
                    ResolutionStrategy.GRADLE_SOURCE_RESOURCES,
                    "Gradle project with source resources (not yet compiled)"
            );
        }

        return null;
    }
}