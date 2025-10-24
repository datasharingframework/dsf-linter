package dev.dsf.linter;

import dev.dsf.linter.input.InputResolver;
import dev.dsf.linter.input.InputType;
import dev.dsf.linter.logger.ConsoleLogger;
import dev.dsf.linter.logger.Logger;
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

@Command(
        name = "dsf-validator",
        mixinStandardHelpOptions = true,
        version = "3.0.0",
        description = "Validates DSF process plugins from local projects, Git repositories, or JAR files."
)
public class Main implements Callable<Integer> {

    @Option(names = {"-p", "--path"},
            description = "Path to local project directory, Git repository URL, or JAR file (local or remote).")
    private String inputPath;

    @Option(names = {"-r", "--report-path"},
            description = "Directory for validation reports. Default: <temp-dir>/dsf-validator-<name>/target/dsf-validation-report")
    private Path reportPath;

    @Option(names = "--html",
            description = "Generate an additional HTML report.")
    private boolean generateHtmlReport = false;

    @Option(names = "--no-fail",
            description = "Exit with code 0 even if validation errors are found.")
    private boolean noFailOnErrors = false;

    @Option(names = {"-v", "--verbose"},
            description = "Enable verbose logging output.")
    private boolean verbose = false;

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


    public static void main(String[] args) {
        boolean verbose = Arrays.stream(args).anyMatch(a -> a.equals("-v") || a.equals("--verbose"));
        configureLogging(verbose);
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        Logger logger = new ConsoleLogger(verbose);
        logger.info("DSF Validator v3.0.0");

        // Validate input
        if (inputPath == null || inputPath.isBlank()) {
            logger.error("ERROR: Specify a path using --path (local directory, Git repository URL, or JAR file).");
            return 1;
        }

        // Resolve input using unified InputResolver
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
            String inputName = extractInputName(inputPath, resolution.inputType());

            // Create report directory separately from project extraction directory
            // This ensures the report survives cleanup of temporary resources
            Path reportBaseDir = tempBase.resolve("dsf-validator-report-" + inputName);
            reportPath = reportBaseDir.resolve("dsf-validation-report");

            logger.info("Validation report will be saved to: " + reportPath.toAbsolutePath());
        }

        try {
            Files.createDirectories(reportPath);
        } catch (IOException e) {
            logger.error("ERROR: Failed to create report directory: " + reportPath, e);
            return 1;
        }

        try {
            // Execute validation
            return runValidation(projectPath, logger);

        } finally {
            // Cleanup temporary resources if needed
            if (resolution.requiresCleanup()) {
                logger.info("\n=== Cleanup Phase ===");
                logger.info("Removing temporary extraction directory...");
                resolver.cleanup(resolution);
                logger.info("Temporary extraction directory removed.");
                logger.info("Validation reports remain available at: " + reportPath.toAbsolutePath());
            }
        }
    }

    private Integer runValidation(Path projectPath, Logger logger) {
        try {
            // Create configuration
            DsfValidatorImpl.Config config = new DsfValidatorImpl.Config(
                    projectPath.toAbsolutePath(),
                    reportPath.toAbsolutePath(),
                    generateHtmlReport,
                    !noFailOnErrors,
                    mavenGoals,
                    skipGoals,
                    logger
            );

            // Create and run validator - handles any number of plugins
            DsfValidatorImpl validator = new DsfValidatorImpl(config);
            DsfValidatorImpl.OverallValidationResult result = validator.validate();

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
     * Print result summary.
     * Works uniformly for any number of plugins.
     */
    private void printResult(DsfValidatorImpl.OverallValidationResult result, Logger logger) {
        // Unified summary for any number of plugins
        logger.info(String.format(
                "\nValidation completed for %d plugin(s) with %d total plugin errors and %d total plugin warnings.",
                result.pluginValidations().size(),
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

        final Path path1 = Paths.get(inputPath);
        switch (inputType) {
            case LOCAL_DIRECTORY -> {
                name = path1.getFileName().toString();
            }
            case GIT_REPOSITORY -> {
                name = inputPath.substring(inputPath.lastIndexOf('/') + 1)
                        .replace(".git", "");
            }
            case LOCAL_JAR_FILE -> {
                name = path1.getFileName().toString().replace(".jar", "");
            }
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