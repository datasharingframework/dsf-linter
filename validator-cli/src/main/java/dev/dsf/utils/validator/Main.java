package dev.dsf.utils.validator;

import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.repo.RepositoryManager;
import dev.dsf.utils.validator.util.*;

import dev.dsf.utils.validator.item.AbstractValidationItem;

import org.eclipse.jgit.api.errors.GitAPIException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * <h2>DSF Validator CLI Entry Point</h2>
 *
 * <p>
 * This is the main class for running the DSF Validator as a command-line application.
 * It supports validation of both BPMN and FHIR resources from:
 * </p>
 * <ul>
 *   <li>A local project directory, using {@code --localPath}</li>
 *   <li>A remote Git repository, using {@code --remoteRepo}</li>
 * </ul>
 *
 * <p>
 * The class performs the following operations:
 * </p>
 * <ol>
 *   <li>Parses command-line arguments using <a href="https://picocli.info/">Picocli</a>.</li>
 *   <li>Clones a remote Git repository if {@code --remoteRepo} is specified.</li>
 *   <li>Builds the project using <a href="https://maven.apache.org/">Apache Maven</a>.</li>
 *   <li>Runs pre- and post-build checks for ServiceLoader registration of
 *       {@code dev.dsf.bpe.v2.ProcessPluginDefinition} using {@link ApiRegistrationValidationSupport}.</li>
 *   <li>Validates all BPMN and FHIR files in the project using {@link DsfValidatorImpl}.</li>
 *   <li>Generates JSON-based validation reports in the {@code report/} directory.</li>
 *   <li>Detects the DSF BPE API version and prints it after validation.</li>
 * </ol>
 *
 * <h3>Command-line Usage</h3>
 * <pre>{@code
 * java -jar dsf-validator.jar --localPath /path/to/local/project
 * java -jar dsf-validator.jar --remoteRepo https://gitlab.com/org/project.git
 * }</pre>
 *
 * <h3>Behavior Notes</h3>
 * <ul>
 *   <li>Exactly one of {@code --localPath} or {@code --remoteRepo} must be provided.</li>
 *   <li>If Maven is not found on the system PATH, validation will fail.</li>
 *   <li>The validator uses Maven to run {@code clean package dependency:copy-dependencies} before validation.</li>
 *   <li>If the {@code ProcessPluginDefinition} is not registered via ServiceLoader, a validation error is shown.</li>
 * </ul>
 *
 * <h3>Report Output</h3>
 * <p>
 * Validation results are written to the {@code report/} directory,
 * structured by resource type (BPMN / FHIR) and severity (success / other).
 * A summary report is also generated at {@code report/aggregated.json}.
 * Additionally, plugin-related items (ServiceLoader checks) are written under {@code report/pluginReports/}
 * and included in the top-level {@code aggregated.json}.
 * </p>
 *
 * @see DsfValidatorImpl
 * @see MavenBuilder
 * @see RepositoryManager
 * @see ApiVersionDetector
 * @see ApiRegistrationValidationSupport
 * @see ValidationOutput
 */
@Command(
        name = "Main",
        mixinStandardHelpOptions = true,
        description = "Validates BPMN and FHIR files from a local path or a remote Git repository."
)
public class Main implements Callable<Integer>
{
    /** Path to a local project directory to be validated. */
    @Option(names = "--localPath", description = "Path to a local project directory.")
    private File localPath;

    /** URL of a remote Git repository to be cloned and validated. */
    @Option(names = "--remoteRepo", description = "URL of a remote Git repository to be cloned.")
    private String remoteRepoUrl;

    /** Main method that initializes command-line processing with Picocli. */
    public static void main(String[] args)
    {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Handles validation process including cloning (if necessary), building with Maven,
     * ServiceLoader preflight checks, plugin report writing, and running BPMN/FHIR validations.
     *
     * @return exit code (0 if successful, non-zero otherwise)
     * @throws Exception if an unexpected error occurs
     */
    @Override
    public Integer call() throws Exception
    {
        // Validate that exactly one of --localPath or --remoteRepo is provided.
        if ((localPath == null || !localPath.isDirectory()) && (remoteRepoUrl == null || remoteRepoUrl.isBlank()))
        {
            System.err.println("ERROR: Specify either --localPath or --remoteRepo.");
            return 1;
        }
        if (localPath != null && remoteRepoUrl != null)
        {
            System.err.println("ERROR: Use only one of --localPath or --remoteRepo, not both.");
            return 1;
        }

        // Determine the project directory (either local or cloned).
        Optional<File> projectDirOpt = (remoteRepoUrl != null)
            ? cloneRepository(remoteRepoUrl)
            : Optional.ofNullable(localPath);

        if (projectDirOpt.isEmpty())
        {
            return 1;
        }

        File projectDir = projectDirOpt.get();

        // Create DsfValidatorImpl instance
        DsfValidatorImpl validator = new DsfValidatorImpl();

        // Clear any previous validation state
        ApiRegistrationValidationSupport.clearReportedCache();

        // Optional: quick pre-build check on the source tree for developer feedback.
        validator.runServiceLoaderCheck("Pre-build", projectDir.toPath());

        // Locate Maven executable and build the project.
        MavenBuilder builder = new MavenBuilder();
        String mavenExecutable = MavenUtil.locateMavenExecutable();
        if (mavenExecutable == null)
        {
            System.err.println("ERROR: Maven executable not found in PATH.");
            return 1;
        }
        if (!builder.buildProject(projectDir, mavenExecutable,
                "-B", "-DskipTests", "-Dformatter.skip=true", "-Dexec.skip=true", "clean", "package", "dependency:copy-dependencies"))
        {
            System.err.println("ERROR: Maven 'package' phase failed.");
            return 1;
        }

        // Post-build check: verify ServiceLoader registration is packaged into build outputs.
        validator.runServiceLoaderCheck("Post-build", projectDir.toPath());

        // Detect and store API version using new enum-based approach
        ApiVersionDetector detector = new ApiVersionDetector();
        var detectedOpt = detector.detect(projectDir.toPath());
        if (detectedOpt.isPresent()) {
            ApiVersionHolder.setVersion(detectedOpt.get().version());
        } else {
            ApiVersionHolder.setVersion(ApiVersion.UNKNOWN);
        }

        //  Plugin reporting (write under configurable report root)
        File reportRoot = new File(System.getProperty("dsf.report.dir", "report"));
        File pluginRoot = new File(reportRoot, "pluginReports");

        // Prepare/clean report directory before writing any reports
        dev.dsf.utils.validator.util.ReportCleaner.prepareCleanReportDirectory(reportRoot);

        // Collect plugin items and write plugin reports
        List<AbstractValidationItem> pluginItems = validator.collectPluginItems(projectDir.toPath());

        // Log plugin items found
        System.out.println("\n=== Plugin Validation Results ===");
        if (pluginItems.isEmpty()) {
            System.out.println("No plugin validation items found.");
        } else {
            System.out.println("Found " + pluginItems.size() + " plugin validation item(s):");
            for (AbstractValidationItem item : pluginItems) {
                System.out.println(" - " + item.getSeverity() + ": " + item.getClass().getSimpleName());
            }
        }

        validator.writePluginReports(pluginItems, pluginRoot);

        // === Run BPMN/FHIR validation as before ===============================
        ValidationOutput output = validator.validate(projectDir.toPath());

        // === Overwrite top-level aggregated.json to include plugin items =======
        List<AbstractValidationItem> combined = new ArrayList<>(output.validationItems());
        combined.addAll(pluginItems);
        new ValidationOutput(combined).writeResultsAsJson(new File(reportRoot, "aggregated.json"));

        System.out.printf("%nValidation finished – %d issue(s) found (%d BPMN/FHIR + %d plugin).%n",
                combined.size(), output.validationItems().size(), pluginItems.size());
        System.out.println("Reports written to: " + reportRoot.getAbsolutePath());
        System.out.println("Plugin reports written to: " + pluginRoot.getAbsolutePath());

        // Print detected API version in red (or 'unknown' if not detected).
        ApiVersion apiVersion = ApiVersionHolder.getVersion();
        String versionStr = switch (apiVersion) {
            case V1 -> "v1";
            case V2 -> "v2";
            case UNKNOWN -> "unknown";
        };
        System.out.println("\u001B[31mDetected DSF BPE API version: " + versionStr + "\u001B[0m");
        return 0;
    }

    //
    // Repository cloning
    //

    /**
     * Clones a remote Git repository into a temporary directory under the system's temp folder.
     *
     * @param remoteUrl the remote repository URL
     * @return an {@link Optional} containing the cloned repository's directory, or empty if cloning fails
     */
    private Optional<File> cloneRepository(String remoteUrl)
    {
        String repositoryName = remoteUrl.substring(remoteUrl.lastIndexOf('/') + 1);
        File cloneDir = new File(System.getProperty("java.io.tmpdir"), repositoryName);

        RepositoryManager repoManager = new RepositoryManager();
        try
        {
            File result = repoManager.getRepository(remoteUrl, cloneDir);
            return Optional.ofNullable(result);
        }
        catch (GitAPIException e)
        {
            System.err.println("ERROR: Failed to clone repository: " + e.getMessage());
            return Optional.empty();
        }
    }
}
