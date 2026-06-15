package dev.dsf.linter;

import dev.dsf.linter.input.InputResolver;
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
 * process plugins from JAR files (local or remote).
 * </p>
 * <p>
 * The linter validates BPMN processes, FHIR resources, and plugin configurations,
 * generating detailed reports in HTML and/or JSON formats.
 * </p>
 * <p>
 * <b>Usage Examples:</b>
 * <pre>
 * # Lint a local JAR file
 * dsf-linter --path C:\path\to\plugin.jar --html
 *
 * # Lint a remote JAR file
 * dsf-linter --path https://example.com/plugin.jar --html
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
        version = "2.0.0",
        description = "Lints DSF process plugins from JAR files (local or remote)."
)
public class Main implements Callable<Integer> {

    @Option(names = {"-p", "--path"},
            description = "Path to JAR file (local or remote URL).")
    private String inputPath;

    @Option(names = {"-r", "--report-path"},
            description = "Directory for linter reports. Default: <temp-dir>/dsf-linter-report-<name>/dsf-linter-report")
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

    @Option(names = "--no-color",
            description = "Disable colored console output. (Default: enabled)")
    private boolean disableColor = false;


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
     *   <li>Resolves the JAR file (downloads if remote)</li>
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

        // Enable colors by default, unless --no-color is specified
        if (!disableColor) {
            Console.enableColors();
        }
        logger.info("DSF Linter v2.0.0");

        // Validate input
        if (inputPath == null || inputPath.isBlank()) {
            logger.error("ERROR: Specify a JAR file path using --path (local path or remote URL).");
            return 1;
        }

        // Validate that input is a JAR file
        if (!inputPath.trim().toLowerCase().endsWith(".jar")) {
            logger.error("ERROR: Input must be a JAR file (ending with .jar). Got: " + inputPath);
            logger.error("Examples:");
            logger.error("  Local:  C:\\path\\to\\plugin.jar");
            logger.error("  Remote: https://example.com/plugin.jar");
            return 1;
        }

        // Resolve the JAR file (download if remote, extract)
        InputResolver resolver = new InputResolver(logger);
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