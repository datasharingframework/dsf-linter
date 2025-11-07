package dev.dsf.linter;

import dev.dsf.linter.logger.Logger;

import java.nio.file.Path;

/**
 * Executor responsible for running the DSF Linter with the provided configuration.
 * <p>
 * This class encapsulates the logic for creating the linter configuration,
 * executing the linting process, and determining the appropriate exit code
 * based on the results.
 * </p>
 *
 * @author DSF Development Team
 * @since 1.2.0
 */
public class LinterExecutor {

    private final Path projectPath;
    private final Path reportPath;
    private final boolean generateHtmlReport;
    private final boolean generateJsonReport;
    private final boolean failOnErrors;
    private final String[] mavenGoals;
    private final String[] skipGoals;
    private final Logger logger;

    /**
     * Constructs a new LinterExecutor with the specified parameters.
     *
     * @param projectPath the path to the project to lint
     * @param reportPath the path where reports should be generated
     * @param generateHtmlReport whether to generate an HTML report
     * @param generateJsonReport whether to generate a JSON report
     * @param failOnErrors whether to fail (exit code 1) if errors are found
     * @param mavenGoals Maven goals to add to the build
     * @param skipGoals Maven goals to remove from the default build
     * @param logger the logger for output
     */
    public LinterExecutor(Path projectPath, Path reportPath,
                          boolean generateHtmlReport, boolean generateJsonReport,
                          boolean failOnErrors, String[] mavenGoals,
                          String[] skipGoals, Logger logger) {
        this.projectPath = projectPath;
        this.reportPath = reportPath;
        this.generateHtmlReport = generateHtmlReport;
        this.generateJsonReport = generateJsonReport;
        this.failOnErrors = failOnErrors;
        this.mavenGoals = mavenGoals;
        this.skipGoals = skipGoals;
        this.logger = logger;
    }

    /**
     * Executes the linting process and returns the result.
     * <p>
     * Creates the linter configuration, runs the linter on the specified project,
     * and returns the overall linting result for further processing.
     * </p>
     *
     * @return the overall linting result
     * @throws Exception if linting fails fatally
     */
    public DsfLinter.OverallLinterResult execute() throws Exception {
        // Create configuration
        DsfLinter.Config config = new DsfLinter.Config(
                projectPath.toAbsolutePath(),
                reportPath.toAbsolutePath(),
                generateHtmlReport,
                generateJsonReport,
                failOnErrors,
                mavenGoals,
                skipGoals,
                logger
        );

        // Create and run linter - handles any number of plugins
        DsfLinter linter = new DsfLinter(config);
        return linter.lint();
    }
}

