package dev.dsf.linter.setup;

import dev.dsf.linter.input.DependencyResolver;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.util.maven.MavenUtil;
import dev.dsf.linter.classloading.ProjectClassLoaderFactory;
import dev.dsf.linter.util.resource.ResourceRootResolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles project setup, build operations, and classpath configuration
 * for DSF validation projects.
 *
 * <p>This class is responsible for:
 * <ul>
 *   <li>Detecting project layout (Maven vs exploded plugin)</li>
 *   <li>Building Maven projects when necessary</li>
 *   <li>Resolving dependencies (stub and/or project-specific)</li>
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
    private final DependencyResolver dependencyResolver;

    /**
     * Constructs a new ProjectSetupHandler with the specified logger.
     *
     * @param logger the logger for output messages
     */
    public ProjectSetupHandler(Logger logger) {
        this.logger = logger;
        this.mavenBuilder = new MavenBuilder();
        this.dependencyResolver = new DependencyResolver(logger);
    }

    /**
     * Sets up the complete validation environment for a project.
     *
     * @param projectPath the path to the project directory
     * @param mavenGoals optional Maven goals to add to the build. If {@code null}, no full Maven build is performed,
     *                   but dependencies are still resolved.
     * @param skipGoals optional Maven goals to remove from default build. Ignored if {@code mavenGoals} is {@code null}.
     * @return a ProjectContext containing all necessary setup information
     * @throws IllegalStateException if setup fails
     * @throws IOException if file operations fail
     * @throws InterruptedException if Maven build is interrupted
     */
    public ProjectContext setupValidationEnvironment(Path projectPath, String[] mavenGoals, String[] skipGoals)
            throws IllegalStateException, IOException, InterruptedException {

        if (!Files.isDirectory(projectPath)) {
            throw new IllegalStateException("Path is not a directory: " + projectPath);
        }

        File projectDir = projectPath.toFile();
        boolean isMavenProject = detectProjectLayout(projectPath);

        if (mavenGoals != null) {
            // User explicitly requested Maven build via --mvn
            logger.info("Maven build enabled via --mvn option. Executing full build...");
            if (!isMavenProject) {
                logger.warn("--mvn specified but no pom.xml found. Maven build will be skipped.");
            } else {
                buildMavenProject(projectDir, mavenGoals, skipGoals);
            }
        } else {
            // No --mvn specified: minimal build for validation
            logger.info("No --mvn option specified. Executing minimal build for validation...");

            if (isMavenProject) {
                // Maven project: compile + merged dependencies (stub + project)
                logger.info("pom.xml detected. Compiling sources and resolving merged dependencies...");
                performMinimalBuild(projectDir);
            } else {
                // No pom.xml: only stub dependencies (for exploded JARs)
                logger.info("No pom.xml found. Using only stub dependencies...");
                dependencyResolver.resolveStubDependencies(projectPath);
            }
        }

        ClassLoader projectClassLoader = createProjectClassLoader(projectDir);

        // Initial resource root resolution based on standard conventions
        ResourceRootResolver.ResolutionResult initialResourceRoot =
                ResourceRootResolver.resolveResourceRoot(projectDir);

        logger.info("Initial resource root resolution: " + initialResourceRoot);

        return new ProjectContext(
                projectPath,
                projectDir,
                initialResourceRoot.resourceRoot(),
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
    private boolean detectProjectLayout(Path projectPath) {
        return Files.isRegularFile(projectPath.resolve("pom.xml"));
    }

    /**
     * Performs a minimal Maven build for validation purposes.
     * <p>
     * This method creates a merged POM combining stub dependencies with project dependencies
     * extracted from the effective POM. It then executes:
     * <ul>
     *   <li>{@code compile} - Compile sources to generate .class files</li>
     *   <li>{@code dependency:copy-dependencies} - Copy all dependencies (stub + project) to target/dependency</li>
     * </ul>
     * </p>
     * <p>
     * This is used when --mvn is NOT specified, ensuring that:
     * <ul>
     *   <li>Source code is compiled (required for ClassLoader to find plugin implementations)</li>
     *   <li>All dependencies are available (stub + project dependencies)</li>
     *   <li>No unnecessary build steps are executed (no clean, no package, no tests)</li>
     * </ul>
     * </p>
     *
     * @param projectDir the project directory
     * @throws IllegalStateException if Maven is not found or build fails
     * @throws InterruptedException if the build process is interrupted
     * @throws IOException if I/O errors occur during build
     */
    private void performMinimalBuild(File projectDir)
            throws IllegalStateException, InterruptedException, IOException {

        dependencyResolver.resolveMergedDependencies(projectDir.toPath());
        logger.info("Minimal build completed successfully.");
    }

    /**
     * Builds a Maven project with default goals, optional user goals, and optional skip goals.
     *
     * <p><b>Build process:</b></p>
     * <ol>
     *   <li>Start with default goals</li>
     *   <li>Remove goals specified in {@code skipGoals}</li>
     *   <li>Add goals from {@code userGoals} (avoiding duplicates, handling property overrides)</li>
     * </ol>
     *
     * <p><b>Default goals:</b></p>
     * <ul>
     *   <li>{@code -B} - Non-interactive batch mode</li>
     *   <li>{@code -q} - Quiet mode, reduces output to only warnings and errors</li>
     *   <li>{@code -DskipTests} - Skip test execution</li>
     *   <li>{@code -Dformatter.skip=true} - Skip code formatting</li>
     *   <li>{@code -Dexec.skip=true} - Skip exec plugin</li>
     *   <li>{@code clean} - Clean build artifacts</li>
     *   <li>{@code package} - Package the project</li>
     *   <li>{@code compile} - Compile sources</li>
     *   <li>{@code dependency:copy-dependencies} - Copy dependencies</li>
     * </ul>
     *
     * <p><b>Goal handling:</b></p>
     * <ul>
     *   <li><b>Goals without "="</b>: Added if not already present (duplicate prevention)</li>
     *   <li><b>Properties with "="</b>: Override default value if different, prevent duplicates if same</li>
     * </ul>
     *
     * <p><b>Examples:</b></p>
     * <pre>
     * userGoals=["validate"], skipGoals=null
     *   → mvn -B -DskipTests ... clean package compile dependency:copy-dependencies validate
     *
     * userGoals=["clean"], skipGoals=null
     *   → mvn -B -DskipTests ... clean package compile dependency:copy-dependencies
     *   (clean already in defaults, not duplicated)
     *
     * userGoals=null, skipGoals=["clean", "package"]
     *   → mvn -B -DskipTests ... compile dependency:copy-dependencies
     *   (clean and package removed)
     *
     * userGoals=["-Dformatter.skip=false", "validate"], skipGoals=["clean"]
     *   → mvn -B -DskipTests -Dformatter.skip=false ... package compile dependency:copy-dependencies validate
     *   (property overridden, clean removed, validate added)
     * </pre>
     *
     * @param projectDir the project directory
     * @param userGoals additional Maven goals to add (can be null or empty)
     * @param skipGoals Maven goals to remove from defaults (can be null or empty)
     * @throws IllegalStateException if Maven is not found or build fails
     * @throws InterruptedException if the build process is interrupted
     * @throws IOException if I/O errors occur during build
     */
    private void buildMavenProject(File projectDir, String[] userGoals, String[] skipGoals)
            throws IllegalStateException, InterruptedException, IOException {
        String mavenExecutable = MavenUtil.locateMavenExecutable();
        if (mavenExecutable == null) {
            throw new IllegalStateException("Maven executable not found in PATH.");
        }

        // Step 1: Define default goals
        List<String> allGoals = new ArrayList<>();
        allGoals.add("-B");
        allGoals.add("-q");
        allGoals.add("-DskipTests");
        allGoals.add("-Dformatter.skip=true");
        allGoals.add("-Dexec.skip=true");
        allGoals.add("clean");
        allGoals.add("package");
        allGoals.add("compile");
        allGoals.add("dependency:copy-dependencies");

        // Step 2: Remove skip goals
        if (skipGoals != null && skipGoals.length > 0) {
            for (String skipGoal : skipGoals) {
                allGoals.remove(skipGoal);
            }
            logger.debug("Removed goals: " + String.join(", ", skipGoals));
        }

        // Step 3: Add user goals (with duplicate prevention and property override handling)
        if (userGoals != null && userGoals.length > 0) {
            for (String userGoal : userGoals) {
                if (userGoal.contains("=")) {
                    // Property with "=" - check for override
                    String propertyKey = userGoal.substring(0, userGoal.indexOf('='));

                    // Remove any existing property with same key
                    allGoals.removeIf(goal -> goal.startsWith(propertyKey + "="));

                    // Add user property
                    allGoals.add(userGoal);
                } else {
                    // Regular goal - add only if not present (duplicate prevention)
                    if (!allGoals.contains(userGoal)) {
                        allGoals.add(userGoal);
                    }
                }
            }
            logger.debug("Added user goals: " + String.join(", ", userGoals));
        }

        logger.info("Executing Maven with goals: " + String.join(" ", allGoals));

        boolean buildOk = mavenBuilder.buildProject(
                projectDir,
                mavenExecutable,
                allGoals.toArray(new String[0])
        );

        if (!buildOk) {
            throw new RuntimeException("Maven build failed.");
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
    public record ProjectContext(Path projectPath, File projectDir, File resourcesDir, boolean isMavenProject,
                                 ClassLoader projectClassLoader) {
    }
}