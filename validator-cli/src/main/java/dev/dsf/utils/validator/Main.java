package dev.dsf.utils.validator;

import dev.dsf.utils.validator.build.MavenBuilder;
import dev.dsf.utils.validator.repo.RepositoryManager;
import dev.dsf.utils.validator.util.MavenUtil;
import dev.dsf.utils.validator.util.ApiVersionDetector;
import org.eclipse.jgit.api.errors.GitAPIException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
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
 * </ul>
 *
 * <h3>Report Output</h3>
 * <p>
 * Validation results are written to the {@code report/} directory,
 * structured by resource type (BPMN / FHIR) and severity (success / other).
 * A summary report is also generated at {@code report/aggregated.json}.
 * </p>
 *
 * @see DsfValidatorImpl
 * @see MavenBuilder
 * @see RepositoryManager
 * @see ApiVersionDetector
 * @see ValidationOutput
 *
 */
@Command(
        name = "Main",
        mixinStandardHelpOptions = true,
        description = "Validates BPMN and FHIR files from a local path or a remote Git repository."
)
public class Main implements Callable<Integer>
{
    /**
     * Path to a local project directory to be validated.
     */
    @Option(names = "--localPath", description = "Path to a local project directory.")
    private File localPath;

    /**
     * URL of a remote Git repository to be cloned and validated.
     */
    @Option(names = "--remoteRepo", description = "URL of a remote Git repository to be cloned.")
    private String remoteRepoUrl;

    /**
     * Main method that initializes command-line processing with Picocli.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args)
    {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Method executed after command-line parsing is completed.
     * Handles validation process including cloning (if necessary), building with Maven, and running validations.
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
        File projectDir = (remoteRepoUrl != null) ? cloneRepository(remoteRepoUrl) : localPath;
        if (projectDir == null)
        {
            return 1;
        }

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

        // Prepare clean report directory
        File reportRoot = new File("report");

        // Detect and store API version before any validation
        String apiVersion = ApiVersionDetector.detectVersion(projectDir.toPath());
        ApiVersionHolder.setVersion(apiVersion);

        // Validate the project files (BPMN and FHIR).
        DsfValidatorImpl validator = new DsfValidatorImpl();
        ValidationOutput output = validator.validate(projectDir.toPath());

        System.out.printf("%nValidation finished – %d issue(s) found.%n", output.validationItems().size());
        System.out.println("Reports written to: " + reportRoot.getAbsolutePath());

        // Print detected API version in red
        System.out.println("\u001B[31mDetected DSF BPE API version: "
                + apiVersion + "\u001B[0m");
        return 0;
    }

    /**
     * Clones a remote Git repository into a temporary directory under the system's temp folder.
     *
     * @param remoteUrl the remote repository URL
     * @return a {@link File} representing the cloned repository's directory, or {@code null} if cloning fails
     */
    private File cloneRepository(String remoteUrl)
    {
        String repositoryName = remoteUrl.substring(remoteUrl.lastIndexOf('/') + 1);
        File cloneDir = new File(System.getProperty("java.io.tmpdir"), repositoryName);

        RepositoryManager repoManager = new RepositoryManager();
        try
        {
            return repoManager.getRepository(remoteUrl, cloneDir);
        }
        catch (GitAPIException e)
        {
            System.err.println("ERROR: Failed to clone repository: " + e.getMessage());
            return null;
        }
    }
}
