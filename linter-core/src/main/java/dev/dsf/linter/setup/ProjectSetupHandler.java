package dev.dsf.linter.setup;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.classloading.ProjectClassLoaderFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles project setup and classpath configuration for extracted DSF JAR files.
 *
 * <p>This class is responsible for:
 * <ul>
 *   <li>Creating and managing project ClassLoaders for extracted JARs</li>
 *   <li>Setting up the linting environment</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class ProjectSetupHandler {

    private final Logger logger;

    /**
     * Constructs a new ProjectSetupHandler with the specified logger.
     *
     * @param logger the logger for output messages
     */
    public ProjectSetupHandler(Logger logger) {
        this.logger = logger;
    }

    /**
     * Sets up the complete linting environment for an extracted JAR file.
     *
     * @param projectPath the path to the extracted JAR directory
     * @return a ProjectContext containing all necessary setup information
     * @throws IllegalStateException if setup fails
     * @throws IOException if file operations fail
     */
    public ProjectContext setupLintingEnvironment(Path projectPath)
            throws IllegalStateException, IOException {

        if (!Files.isDirectory(projectPath)) {
            throw new IllegalStateException("Path is not a directory: " + projectPath);
        }

        File projectDir = projectPath.toFile();
        logger.info("Setting up linting environment for extracted JAR...");

        ClassLoader projectClassLoader = createProjectClassLoader(projectDir);

        return new ProjectContext(
                projectPath,
                projectDir,
                false, // Not a Maven project (always extracted JAR)
                projectClassLoader
        );
    }

    /**
     * Creates a project-specific ClassLoader for the given project directory.
     *
     * @param projectDir the project directory
     * @return the created ClassLoader
     * @throws IllegalStateException if ClassLoader creation fails
     */
    private ClassLoader createProjectClassLoader(File projectDir) throws IllegalStateException {
        try {
            ClassLoader classLoader = ProjectClassLoaderFactory.getOrCreateProjectClassLoader(projectDir);
            logger.debug("Retrieved project ClassLoader for: " + projectDir.getAbsolutePath());
            return classLoader;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to create project classpath for project at " + projectDir, e);
        }
    }

    /**
     * Data class representing the project context after setup.
     */
    public record ProjectContext(Path projectPath, File projectDir, boolean isMavenProject,
                                 ClassLoader projectClassLoader) {
    }
}