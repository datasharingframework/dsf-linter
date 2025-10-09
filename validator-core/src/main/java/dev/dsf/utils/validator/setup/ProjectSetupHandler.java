package dev.dsf.utils.validator.setup;

import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.util.maven.MavenUtil;
import dev.dsf.utils.validator.classloading.ProjectClassLoaderFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles project setup, build operations, and classpath configuration
 * for DSF validation projects.
 *
 * <p>This class is responsible for:
 * <ul>
 *   <li>Detecting project layout (Maven vs exploded plugin)</li>
 *   <li>Building Maven projects when necessary</li>
 *   <li>Creating and managing project ClassLoaders</li>
 *   <li>Setting up the validation environment</li>
 * </ul>
 *
 * @author DSF Development Team
 * @since 1.0.0
 */
public class ProjectSetupHandler {

    private final Logger logger;
    private final MavenBuilder mavenBuilder;

    /**
     * Constructs a new ProjectSetupHandler with the specified logger.
     *
     * @param logger the logger for output messages
     */
    public ProjectSetupHandler(Logger logger) {
        this.logger = logger;
        this.mavenBuilder = new MavenBuilder();
    }

    /**
     * Sets up the complete validation environment for a project.
     *
     * @param projectPath the path to the project directory
     * @return a ProjectContext containing all necessary setup information
     * @throws IllegalStateException if setup fails
     * @throws IOException if file operations fail
     * @throws InterruptedException if Maven build is interrupted
     */
    public ProjectContext setupValidationEnvironment(Path projectPath)
            throws IllegalStateException, IOException, InterruptedException {

        if (!Files.isDirectory(projectPath)) {
            throw new IllegalStateException("Path is not a directory: " + projectPath);
        }

        File projectDir = projectPath.toFile();
        boolean isMavenProject = detectProjectLayout(projectPath);

        ClassLoader projectClassLoader;
        File resourcesDir;

        if (isMavenProject) {
            logger.info("Detected Maven project ('pom.xml' exists), executing build...");

            buildMavenProject(projectDir);
            projectClassLoader = createProjectClassLoader(projectDir);

            // Determine resources directory for Maven project
            File srcMainResources = new File(projectDir, "src/main/resources");
            resourcesDir = srcMainResources.isDirectory()
                    ? srcMainResources
                    : new File(projectDir, "target/classes");

            logger.info("Using Maven resource directory: " + resourcesDir.getAbsolutePath());
        } else {
            logger.info("No 'pom.xml' found. Assuming exploded plugin layout – skipping Maven build.");
            logger.info("Building runtime classpath from: " + projectDir.getAbsolutePath());

            projectClassLoader = createProjectClassLoader(projectDir);
            resourcesDir = projectDir;

            logger.info("Using project root as resource directory for exploded plugin: "
                    + resourcesDir.getAbsolutePath());
        }

        // Set the thread context class loader
        Thread.currentThread().setContextClassLoader(projectClassLoader);
        logger.info("Context ClassLoader set for validation.");

        return new ProjectContext(
                projectPath,
                projectDir,
                resourcesDir,
                isMavenProject,
                projectClassLoader
        );
    }

    /**
     * Detects whether the project is a Maven project by checking for pom.xml.
     *
     * @param projectPath the project path to check
     * @return true if pom.xml exists, false otherwise
     */
    public boolean detectProjectLayout(Path projectPath) {
        return Files.isRegularFile(projectPath.resolve("pom.xml"));
    }

    /**
     * Builds a Maven project using clean, package, and dependency:copy-dependencies goals.
     *
     * @param projectDir the project directory
     * @throws IllegalStateException if Maven is not found or build fails
     * @throws InterruptedException if the build process is interrupted
     */
    private void buildMavenProject(File projectDir) throws IllegalStateException, InterruptedException, IOException {
        String mavenExecutable = MavenUtil.locateMavenExecutable();
        if (mavenExecutable == null) {
            throw new IllegalStateException("Maven executable not found in PATH.");
        }

        boolean buildOk = mavenBuilder.buildProject(
                projectDir,
                mavenExecutable,
                "-B",
                "-DskipTests",
                "-Dformatter.skip=true",
                "-Dexec.skip=true",
                "clean",
                "package",
                "dependency:copy-dependencies"
        );

        if (!buildOk) {
            throw new RuntimeException("Maven 'package' phase failed.");
        }

        logger.info("Maven build completed successfully.");
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
            ClassLoader classLoader = ProjectClassLoaderFactory.createProjectClassLoader(projectDir);
            logger.debug("Created project ClassLoader for: " + projectDir.getAbsolutePath());
            return classLoader;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to create project classpath for project at " + projectDir, e);
        }
    }

    /**
     * Restores the previous ClassLoader context.
     *
     * @param previousClassLoader the ClassLoader to restore
     */
    public void restoreClassLoader(ClassLoader previousClassLoader) {
        Thread.currentThread().setContextClassLoader(previousClassLoader);
        logger.debug("Restored previous context ClassLoader.");
    }

    /**
     * Data class representing the project context after setup.
     */
    public record ProjectContext(Path projectPath, File projectDir, File resourcesDir, boolean isMavenProject,
                                 ClassLoader projectClassLoader) {
    }
}