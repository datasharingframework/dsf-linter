package dev.dsf.linter;

import dev.dsf.linter.input.InputResolver;
import dev.dsf.linter.input.InputType;
import dev.dsf.linter.logger.ConsoleLogger;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.logger.Console;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Command-line interface entry point for the DSF Linter application.
 * <p>
 * This class provides a command-line interface for linting DSF (Data Sharing Framework)
 * process plugins from various input sources:
 * <ul>
 *   <li>Local project directories</li>
 *   <li>Git repositories (via URL)</li>
 *   <li>JAR files (local or remote)</li>
 * </ul>
 * </p>
 * <p>
 * The linter validates BPMN processes, FHIR resources, and plugin configurations,
 * generating detailed reports in HTML and/or JSON formats.
 * </p>
 * <p>
 * <b>Usage Examples:</b>
 * <pre>
 * # Lint a local JAR file
 * dsf-linter --path /path/to/plugin.jar --html
 *
 * # Lint a local project with Maven build
 * dsf-linter --path /path/to/project --mvn clean package --html
 *
 * # Lint from Git repository
 * dsf-linter --path https://github.com/user/repo.git --mvn clean package
 *
 * # Lint with custom report location
 * dsf-linter --path plugin.jar --report-path ./reports --html --json
 * </pre>
 * </p>
 *
 * @see DsfLinter
 * @see InputResolver
 */
@Command(
        name = "dsf-linter",
        mixinStandardHelpOptions = true,
        version = "1.2.0",
        description = "Lints DSF process plugins from local projects, Git repositories, or JAR files."
)
public class Main implements Callable<Integer> {

    @Option(names = {"-p", "--path"},
            description = "Path to local project directory, Git repository URL, or JAR file (local or remote).")
    private String inputPath;

    @Option(names = {"-r", "--report-path"},
            description = "Directory for linter reports. Default: <temp-dir>/dsf-linter-<name>/target/dsf-linter-report")
    private Path reportPath;

    @Option(names = "--html",
            description = "Generate an HTML report.")
    private boolean generateHtmlReport = false;

    @Option(names = "--json",
            description = "Generate a JSON report.")
    private boolean generateJsonReport = false;

    @Option(names = "--no-fail",
            description = "Exit with code 0 even if linter errors are found.")
    private boolean noFailOnErrors = false;

    @Option(names = {"-v", "--verbose"},
            description = "Enable verbose logging output.")
    private boolean verbose = false;

    @Option(names = "--color",
            description = "Enable colored console output. (Default: disabled)")
    private boolean enableColor = false;

    /**
     * Maven goals to add to the build.
     * These goals are added to the default goals (avoiding duplicates).
     * Properties with "=" will override defaults if the value differs.
     *
     * <p><b>Examples:</b></p>
     * <pre>
     * --mvn validate test                        // Adds validate and test goals
     * --mvn -Dformatter.skip=false              // Overrides default property value
     * --mvn clean validate                       // clean already in defaults (1x), validate added
     * </pre>
     */
    @Option(names = "--mvn",
            arity = "0..*",
            description = "Add Maven goals to the build. " +
                    "Example: --mvn validate test")
    private String[] mavenGoals;

    /**
     * Maven goals to remove from the default build.
     *
     * <p><b>Examples:</b></p>
     * <pre>
     * --skip clean package                       // Removes clean and package from defaults
     * --skip compile                             // Removes compile from defaults
     * </pre>
     */
    @Option(names = "--skip",
            arity = "1..*",
            description = "Remove Maven goals from the default build. " +
                    "Example: --skip clean package")
    private String[] skipGoals;


    /**
     * Main entry point for the DSF Linter CLI application.
     * <p>
     * Configures logging based on verbosity settings and executes the command-line
     * interface using PicoCLI. The application exits with an appropriate status code
     * (0 for success, 1 for failure).
     * </p>
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        boolean verbose = Arrays.stream(args).anyMatch(a -> a.equals("-v") || a.equals("--verbose"));
        configureLogging(verbose);
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Executes the linting process based on the provided command-line options.
     * <p>
     * This method:
     * <ol>
     *   <li>Validates input parameters</li>
     *   <li>Detects and resolves the input type (directory, Git repo, JAR)</li>
     *   <li>Prepares the report directory</li>
     *   <li>Executes the linting process</li>
     *   <li>Cleans up temporary resources</li>
     * </ol>
     * </p>
     *
     * @return exit code (0 for success, 1 for failure)
     */
    @Override
    public Integer call() {
        Logger logger = new ConsoleLogger(verbose);

        // Enable colors if requested
        if (enableColor) {
            Console.enableColors();
        }
        logger.info("DSF Linter v1.0.0");

        // Validate input
        if (inputPath == null || inputPath.isBlank()) {
            logger.error("ERROR: Specify a path using --path (local directory, Git repository URL, or JAR file).");
            return 1;
        }

        // Early detection of input type BEFORE any resolution/cloning/downloading
        InputResolver resolver = new InputResolver(logger);
        InputType inputType;

        try {
            inputType = resolver.detectInputType(inputPath);
            logger.info("Detected input type: " + inputType);
        } catch (IllegalArgumentException e) {
            logger.error("ERROR: Invalid input: " + e.getMessage());
            return 1;
        }

        // Check if non-JAR input without --mvn option BEFORE resolution
        if (mavenGoals == null &&
                inputType != InputType.LOCAL_JAR_FILE &&
                inputType != InputType.REMOTE_JAR_URL) {

            String errorMessage = String.format("""
            
            ╔═══════════════════════════════════════════════════════════════
              ERROR: The linter is primarily designed for JAR files.
              For project directories or Git repositories, please use
              the --mvn option to ensure a proper build.
            
              Example: dsf-linter --path %s --mvn clean package
            
              See README.md for more details.
            ╚═══════════════════════════════════════════════════════════════
            """, inputPath);

            Console.redErr(errorMessage);
            return 1;
        }

        // Check if JAR with --mvn option (unnecessary but harmless)
        if (mavenGoals != null &&
                (inputType == InputType.LOCAL_JAR_FILE || inputType == InputType.REMOTE_JAR_URL)) {

            logger.warn("");
            logger.warn("╔══════════════════════════════════════════════════════════════");
            logger.warn("  NOTE: --mvn option has no effect on JAR files.");
            logger.warn("  JAR files always use stub dependencies only.");
            logger.warn("  The --mvn option will be ignored.");
            logger.warn("╚══════════════════════════════════════════════════════════════");
            logger.warn("");
        }

        // NOW we can safely resolve the input (clone, download, etc.)
        Optional<InputResolver.ResolutionResult> resolutionResult = resolver.resolve(inputPath);

        if (resolutionResult.isEmpty()) {
            logger.error("ERROR: Failed to resolve input: " + inputPath);
            return 1;
        }

        InputResolver.ResolutionResult resolution = resolutionResult.get();
        Path projectPath = resolution.resolvedPath();

        logger.info("Resolved project path: " + projectPath.toAbsolutePath());

        // Set default report path if not specified (always in temp directory)
        if (reportPath == null) {
            Path tempBase = Paths.get(System.getProperty("java.io.tmpdir"));
            String inputName = extractInputName(inputPath, resolution.inputType());

            // Create report directory separately from project extraction directory
            // This ensures the report survives cleanup of temporary resources
            Path reportBaseDir = tempBase.resolve("dsf-linter-report-" + inputName);
            reportPath = reportBaseDir.resolve("dsf-linter-report");

            logger.info("Linter report will be saved to: " + reportPath.toAbsolutePath());
        }

        try {
            if (Files.exists(reportPath)) {
                logger.debug("Removing existing report directory to avoid stale files...");
                deleteDirectoryRecursively(reportPath);
                logger.debug("Existing report directory removed.");
            }

            Files.createDirectories(reportPath);
        } catch (IOException e) {
            logger.error("ERROR: Failed to prepare report directory: " + reportPath, e);
            return 1;
        }

        try {
            // Execute linting
            return runLinter(projectPath, logger);

        } finally {
            // Cleanup temporary resources if needed
            if (resolution.requiresCleanup()) {
                logger.info("\n=== Cleanup Phase ===");
                logger.info("Removing temporary extraction directory...");
                resolver.cleanup(resolution);
                logger.info("Temporary extraction directory removed.");
            }
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param directory the directory to delete
     * @throws IOException if deletion fails
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Runs the DSF Linter on the specified project path.
     * <p>
     * Creates the linter configuration, executes the linting process,
     * and returns the appropriate exit code based on the results.
     * </p>
     *
     * @param projectPath the path to the project to lint
     * @param logger the logger for output
     * @return exit code (0 for success, 1 for failure)
     */
    private Integer runLinter(Path projectPath, Logger logger) {
        try {
            // Create configuration
            DsfLinter.Config config = new DsfLinter.Config(
                    projectPath.toAbsolutePath(),
                    reportPath.toAbsolutePath(),
                    generateHtmlReport,
                    generateJsonReport,
                    !noFailOnErrors,
                    mavenGoals,
                    skipGoals,
                    logger
            );

            // Create and run linter - handles any number of plugins
            DsfLinter linter = new DsfLinter(config);
            DsfLinter.OverallLinterResult result = linter.lint();

            // Output summary
            printResult(result, logger);

            // Return exit code
            return result.success() ? 0 : 1;

        } catch (Exception e) {
            logger.error("FATAL: " + e.getMessage(), e);
            return 1;
        }
    }

    /**
     * Prints a summary of the linting results.
     * <p>
     * Works uniformly for any number of plugins, displaying total errors,
     * warnings, and leftover resources found.
     * </p>
     *
     * @param result the overall linting result
     * @param logger the logger for output
     */
    private void printResult(DsfLinter.OverallLinterResult result, Logger logger) {
        // Unified summary for any number of plugins
        logger.info(String.format(
                "\nLinting completed for %d plugin(s) with %d total plugin errors and %d total plugin warnings.",
                result.pluginLinter().size(),
                result.getPluginErrors(),
                result.getPluginWarnings()
        ));

        if (result.getLeftoverCount() > 0) {
            logger.info(String.format(
                    "Additionally, %d unreferenced project-level resource(s) were found.",
                    result.getLeftoverCount()
            ));
        }

        logger.info("Reports written to: " + result.masterReportPath().toUri());
    }

    /**
     * Configures the logging system based on verbosity level.
     * <p>
     * Selects the appropriate logback configuration file based on whether
     * verbose logging is enabled.
     * </p>
     *
     * @param verbose whether verbose logging should be enabled
     */
    private static void configureLogging(boolean verbose)
    {
        String logbackConfigurationFile = verbose ? "logback-verbose.xml" : "logback.xml";
        System.setProperty("logback.configurationFile", logbackConfigurationFile);
    }

    /**
     * Extracts a safe name from the input path for use in temporary directory names.
     *
     * @param inputPath the original input path
     * @param inputType the detected input type
     * @return a sanitized name suitable for directory names
     */
    private String extractInputName(String inputPath, InputType inputType) {
        String name;

        switch (inputType) {
            case LOCAL_DIRECTORY, LOCAL_JAR_FILE -> {
                Path path = Paths.get(inputPath);
                name = path.getFileName().toString();
                if (inputType == InputType.LOCAL_JAR_FILE) {
                    name = name.replace(".jar", "");
                }
            }
            case GIT_REPOSITORY ->
                    name = inputPath.substring(inputPath.lastIndexOf('/') + 1)
                            .replace(".git", "");

            case REMOTE_JAR_URL -> {
                String path = inputPath.substring(inputPath.lastIndexOf('/') + 1);
                int queryIndex = path.indexOf('?');
                if (queryIndex > 0) {
                    path = path.substring(0, queryIndex);
                }
                name = path.replace(".jar", "");
            }
            default -> name = "unknown";
        }

        // Sanitize name for use in file system
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}