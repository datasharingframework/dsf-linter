package dev.dsf.utils.validator.plugin;

import dev.dsf.utils.validator.DsfValidatorImpl;
import dev.dsf.utils.validator.logger.Logger;
import dev.dsf.utils.validator.plugin.logger.MavenMojoLogger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * DSF Validator Maven Plugin – Mojo: verify
 * Integrates DSF validation into the Maven build lifecycle using the unified validator.
 */
@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class ValidateMojo extends AbstractMojo {

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(getClass());

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File baseDirectory;

    @Parameter(property = "dsf.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "dsf.report.dir")
    private File reportDirectory;

    @Parameter(property = "dsf.htmlReport", defaultValue = "true")
    private boolean generateHtmlReport;

    @Parameter(property = "dsf.failOnErrors", defaultValue = "true")
    private boolean failOnErrors;

    /**
     * Enables verbose logging to include SUCCESS items in the console output.
     * Can be activated via the command line with -Ddsf.validation.verbose=true
     */
    @Parameter(property = "dsf.validation.verbose", defaultValue = "false")
    private boolean verbose;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            logger.info("DSF validation is skipped (dsf.skip=true).");
            return;
        }

        // Pass the verbose flag to the logger
        Logger validatorLogger = new MavenMojoLogger(logger, verbose);

        if (reportDirectory == null) {
            reportDirectory = new File(baseDirectory, "target/dsf-validation-report");
        }

        // Check if the directory could be created AND if it does not already exist.
        if (!reportDirectory.mkdirs() && !reportDirectory.isDirectory()) {
            throw new MojoExecutionException("Could not create report directory: " + reportDirectory);
        }

        try {
            runValidation(validatorLogger);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during validation.", e);
            throw new MojoExecutionException("A fatal error occurred, see logs for details.", e);
        }
    }

    /**
     * Executes the validation using the unified validator.
     * The validator handles any number of plugins uniformly.
     */
    private void runValidation(Logger validatorLogger) throws Exception {
        logger.info("Starting DSF validation...");

        // Create configuration for the unified validator
        DsfValidatorImpl.Config config = new DsfValidatorImpl.Config(
                baseDirectory.toPath().toAbsolutePath(),
                reportDirectory.toPath().toAbsolutePath(),
                generateHtmlReport,
                failOnErrors,
                validatorLogger
        );

        DsfValidatorImpl validator = new DsfValidatorImpl(config);
        DsfValidatorImpl.ValidationResult result = validator.validate();

        // Provide unified feedback for any number of plugins
        logger.info("Completed validation for {} plugin(s).", result.pluginValidations().size());

        if (!result.success()) {
            throw new MojoFailureException("DSF validation failed with "
                    + result.getTotalErrors() + " errors. See reports in "
                    + reportDirectory.getAbsolutePath());
        }

        logger.info("DSF validation finished successfully.");
    }
}