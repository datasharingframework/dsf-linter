package dev.dsf.linter;

import dev.dsf.linter.input.InputResolver;
import dev.dsf.linter.input.InputType;
import dev.dsf.linter.logger.ConsoleLogger;
import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.logger.Console;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            String inputName = resolver.extractInputName(inputPath, resolution.inputType());

            // Create report directory separately from project extraction directory
            // This ensures the report survives cleanup of temporary resources
            Path reportBaseDir = tempBase.resolve("dsf-linter-report-" + inputName);
            reportPath = reportBaseDir.resolve("dsf-linter-report");

            logger.info("Linter report will be saved to: " + reportPath.toAbsolutePath());
        }

        try {
            if (Files.exists(reportPath)) {
                logger.debug("Removing existing report directory to avoid stale files...");
                resolver.deleteDirectoryRecursively(reportPath);
                logger.debug("Existing report directory removed.");
            }

            Files.createDirectories(reportPath);
        } catch (IOException e) {
            logger.error("ERROR: Failed to prepare report directory: " + reportPath, e);
            return 1;
        }

        try {
            // Execute linting
            LinterExecutor executor = new LinterExecutor(
                    projectPath,
                    reportPath,
                    generateHtmlReport,
                    generateJsonReport,
                    !noFailOnErrors,
                    mavenGoals,
                    skipGoals,
                    logger
            );

            DsfLinter.OverallLinterResult result = executor.execute();

            // Output summary
            ResultPrinter.printResult(result, logger);

            // Return exit code
            return result.success() ? 0 : 1;

        } catch (Exception e) {
            logger.error("FATAL: " + e.getMessage(), e);
            return 1;

        } finally {
            if (resolution.requiresCleanup()) {
                logger.info("\n=== Cleanup Phase ===");
                logger.info("Removing temporary extraction directory...");
                resolver.cleanup(resolution);
                logger.info("Temporary extraction directory removed.");
            }
        }
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
}