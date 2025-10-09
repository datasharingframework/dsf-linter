package dev.dsf.utils.validator;

import dev.dsf.utils.validator.logger.ConsoleLogger;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.repo.RepositoryManager;
import org.eclipse.jgit.api.errors.GitAPIException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(
        name = "dsf-validator",
        mixinStandardHelpOptions = true,
        version = "3.0.0",
        description = "Validates DSF process plugins from a local project or a remote Git repository."
)
public class Main implements Callable<Integer> {

    @Option(names = {"-p", "--path"},
            description = "Path to the local project directory or URL of a remote Git repository.")
    private String inputPath;

    @Option(names = {"-r", "--report-path"},
            description = "Directory for validation reports. Default: <project-path>/target/dsf-validation-report")
    private Path reportPath;

    @Option(names = "--html",
            description = "Generate an additional HTML report.")
    private boolean generateHtmlReport = true;

    @Option(names = "--no-fail",
            description = "Exit with code 0 even if validation errors are found.")
    private boolean noFailOnErrors = false;

    @Option(names = {"-v", "--verbose"},
            description = "Enable verbose logging output.")
    private boolean verbose = false;


    public static void main(String[] args) {
        boolean verbose = Arrays.stream(args).anyMatch(a -> a.equals("-v") || a.equals("--verbose"));
        configureLogging(verbose);
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        Logger logger = new ConsoleLogger(verbose);
        logger.info("DSF Validator v3.0.0");

        // Validate input
        if (inputPath == null || inputPath.isBlank()) {
            logger.error("ERROR: Specify a path using --path (local directory or Git repository URL).");
            return 1;
        }

        // Determine project path (local or cloned)
        Path projectPath = resolveProjectPath(inputPath, logger);
        if (projectPath == null) {
            return 1;
        }

        // Set default report path if not specified
        if (reportPath == null) {
            reportPath = projectPath.resolve("target").resolve("dsf-validation-report");
        }
        reportPath.toFile().mkdirs();

        // Execute unified validation
        return runValidation(projectPath, logger);
    }

    /**
     * Run validation using the unified validator.
     * The validator handles any number of plugins uniformly.
     */
    private Integer runValidation(Path projectPath, Logger logger) {
        try {
            // Create configuration
            DsfValidatorImpl.Config config = new DsfValidatorImpl.Config(
                    projectPath.toAbsolutePath(),
                    reportPath.toAbsolutePath(),
                    generateHtmlReport,
                    !noFailOnErrors,
                    logger
            );

            // Create and run validator - handles any number of plugins
            DsfValidatorImpl validator = new DsfValidatorImpl(config);
            DsfValidatorImpl.ValidationResult result = validator.validate();

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
    private void printResult(DsfValidatorImpl.ValidationResult result, Logger logger) {
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

    /**
     * Resolves the project path from input (local or remote).
     */
    private Path resolveProjectPath(String input, Logger logger) {
        if (isRemoteRepository(input)) {
            // Clone remote repository
            Optional<Path> clonedPath = cloneRepository(input, logger);
            if (clonedPath.isEmpty()) {
                logger.error("Failed to clone repository");
                return null;
            }
            logger.info("Successfully cloned repository to: " + clonedPath.get());
            return clonedPath.get();
        } else {
            // Local path
            Path localPath = Path.of(input);
            if (!Files.exists(localPath) || !Files.isDirectory(localPath)) {
                logger.error("ERROR: Path does not exist or is not a directory: " + input);
                return null;
            }
            return localPath;
        }
    }

    private boolean isRemoteRepository(String input) {
        return input != null && (
                input.startsWith("http://") ||
                        input.startsWith("https://") ||
                        input.startsWith("git://") ||
                        input.startsWith("ssh://") ||
                        input.contains("git@")
        );
    }

    private Optional<Path> cloneRepository(String remoteUrl, Logger logger) {
        String repositoryName = remoteUrl.substring(remoteUrl.lastIndexOf('/') + 1)
                .replace(".git", "");
        //dsf-validator-<repositoryName> to avoid collisions with real project directories
        Path clonePath = Path.of(System.getProperty("java.io.tmpdir"), "dsf-validator-" + repositoryName);

        if (Files.exists(clonePath)) {
            deleteDirectoryRecursively(clonePath);
        }

        RepositoryManager repoManager = new RepositoryManager();
        try {
            File result = repoManager.getRepository(remoteUrl, clonePath.toFile());
            return Optional.ofNullable(result).map(File::toPath);
        } catch (GitAPIException e) {
            logger.error("ERROR: Failed to clone repository: " + e.getMessage());
            return Optional.empty();
        }
    }

    private void deleteDirectoryRecursively(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            System.err.println("Warning: Could not delete file: " + file);
                        }
                    });
        } catch (IOException e) {
            System.err.println("Warning: Could not recursively delete directory: " + path);
        }
    }

    /**
     * Configures the logging framework based on the verbose flag.
     *
     * @param verbose If true, loads the verbose logging configuration.
     * Otherwise, loads the default (non-verbose) configuration.
     */
    private static void configureLogging(boolean verbose)
    {
        String logbackConfigurationFile = verbose ? "logback-verbose.xml" : "logback.xml";
        System.setProperty("logback.configurationFile", logbackConfigurationFile);
    }
}